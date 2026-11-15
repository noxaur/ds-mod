import { Context, Effect, Layer } from "effect";
import { HttpRouter, HttpServer, HttpServerRequest } from "effect/http";
import { HttpApiBuilder, HttpApiError } from "effect/http-api";

import { Api } from "./api.ts";
import * as Contrib from "./contrib/contrib.ts";
import { Store } from "./contrib/store.ts";

/**
 * Who is calling, in two headers only [asCaller] may set. It deletes whatever the client sent under those names
 * first, so a client cannot name itself.
 */
const USER_HEADER = "x-dsmod-user";
const ADMIN_HEADER = "x-dsmod-admin";

/** Compares in the same time whether or not the first characters match, so the admin token cannot be guessed a letter at a time. */
function same(given: string, expected: string) {
  let difference = given.length ^ expected.length;
  for (let index = 0; index < given.length; index++) {
    difference |= given.charCodeAt(index) ^ expected.charCodeAt(index % Math.max(expected.length, 1));
  }
  return difference === 0;
}

/**
 * The request as the API will believe it: from [userId], the signed-in player if there is one, and from an agent
 * when it carries [adminToken]. With no admin token configured, nobody is an agent.
 */
export function asCaller(request: Request, userId: string | null, adminToken: string | undefined): Request {
  const headers = new Headers(request.headers);
  headers.delete(USER_HEADER);
  headers.delete(ADMIN_HEADER);
  if (userId !== null && userId !== "") {
    headers.set(USER_HEADER, userId);
  }
  const given = request.headers.get("authorization") ?? "";
  if (adminToken !== undefined && adminToken !== "" && same(given, `Bearer ${adminToken}`)) {
    headers.set(ADMIN_HEADER, "1");
  }
  return new Request(request, { headers });
}

const player = Effect.gen(function* () {
  const userId = (yield* HttpServerRequest.HttpServerRequest).headers[USER_HEADER];
  return userId === undefined || userId === "" ? yield* new HttpApiError.Unauthorized() : userId;
});

const admin = Effect.gen(function* () {
  if ((yield* HttpServerRequest.HttpServerRequest).headers[ADMIN_HEADER] !== "1") {
    yield* new HttpApiError.Unauthorized();
  }
});

const Health = HttpApiBuilder.group(Api, "health", (handlers) => handlers.handle("healthz", () => Effect.void));

const ContribLive = HttpApiBuilder.group(Api, "contrib", (handlers) =>
  handlers
    .handle("offers", () => Effect.flatMap(player, Contrib.offersFor))
    .handle("skip", ({ params }) => Effect.flatMap(player, (userId) => Contrib.skip(params.id, userId)))
    .handle("submit", ({ params, query, payload }) =>
      Effect.flatMap(player, (userId) => Contrib.submit(params.id, userId, query.modVersion, payload)),
    )
    .handle("report", ({ payload }) => Effect.andThen(player, Contrib.report(payload.modVersion, payload.sightings))),
);

const AdminLive = HttpApiBuilder.group(Api, "admin", (handlers) =>
  handlers
    .handle("create", ({ payload }) => Effect.andThen(admin, Contrib.createRequest(payload)))
    .handle("requests", () => Effect.andThen(admin, Contrib.reports))
    .handle("close", ({ params }) => Effect.andThen(admin, Contrib.close(params.id)))
    .handle("capture", ({ params }) => Effect.andThen(admin, Contrib.captureOf(params.id)))
    .handle("unknowns", () => Effect.andThen(admin, Contrib.unknowns)),
);

const web = HttpRouter.toWebHandler(
  HttpApiBuilder.layer(Api).pipe(
    Layer.provide([Health, ContribLive, AdminLive]),
    Layer.provide(HttpServer.layerServices),
  ),
  { disableLogger: true },
);

/**
 * The API as a function from request to response, keeping its rows and files in [store]. [caller] says who a request
 * is from, and is the only thing that can.
 */
export const makeApp = (
  store: Store["Service"],
  caller: (request: Request) => { readonly userId: string | null; readonly adminToken: string | undefined },
) => {
  const context = Context.make(Store, store);
  return (request: Request) => {
    const { userId, adminToken } = caller(request);
    return web.handler(asCaller(request, userId, adminToken), context);
  };
};
