import { ByteSize, Cause, Clock, Context, Effect, ErrorReporter, Layer, Option, Redacted } from "effect";
import { HttpIncomingMessage, HttpRouter, HttpServer, HttpServerError } from "effect/http";
import { HttpApiBuilder, HttpApiError, HttpApiMiddleware } from "effect/http-api";

import { Agent, Api, Caller, Player, SignedIn } from "./api.ts";
import * as Contrib from "./contrib/contrib.ts";
import { Store } from "./contrib/store.ts";
import * as Items from "./items/items.ts";
import { ItemStore } from "./items/store.ts";
import * as Profiles from "./profiles/profiles.ts";
import { ProfileStore } from "./profiles/store.ts";
import { Upstream } from "./profiles/upstream.ts";

// @keep Constant time, so the admin token cannot be guessed a letter at a time.
function same(given: string, expected: string) {
  let difference = given.length ^ expected.length;
  for (let index = 0; index < given.length; index++) {
    difference |= given.charCodeAt(index) ^ expected.charCodeAt(index % Math.max(expected.length, 1));
  }
  return difference === 0;
}

// @keep A middleware may only require services the app is built with, and Caller comes with each request.
const caller = Effect.map(
  Effect.serviceOption(Caller),
  Option.getOrElse(() => ({ userId: null, adminToken: undefined })),
);

const SignedInLive = Layer.succeed(SignedIn)({
  session: (handler) =>
    Effect.gen(function* () {
      const { userId } = yield* caller;
      if (userId === null || userId === "") {
        return yield* new HttpApiError.Unauthorized();
      }
      return yield* Effect.provideService(handler, Player, { userId });
    }),
});

const AgentLive = Layer.succeed(Agent)({
  adminToken: (handler, { credential }) =>
    Effect.gen(function* () {
      const { adminToken } = yield* caller;
      if (adminToken === undefined || adminToken === "" || !same(Redacted.value(credential), adminToken)) {
        return yield* new HttpApiError.Unauthorized();
      }
      return yield* handler;
    }),
});

const Health = HttpApiBuilder.group(Api, "health", (handlers) => handlers.handle("healthz", () => Effect.void));

const ContribLive = HttpApiBuilder.group(Api, "contrib", (handlers) =>
  handlers
    .handle("offers", () => Effect.flatMap(Player, ({ userId }) => Contrib.offersFor(userId)))
    .handle("skip", ({ params }) => Effect.flatMap(Player, ({ userId }) => Contrib.skip(params.id, userId)))
    .handle("submit", ({ params, query, payload }) =>
      Effect.flatMap(Player, ({ userId }) => Contrib.submit(params.id, userId, query.modVersion, payload)),
    )
    .handle("items", ({ payload }) =>
      Effect.flatMap(Player, ({ userId }) => Items.report(userId, payload.modVersion, payload.items)),
    )
    .handle("report", ({ payload }) => Contrib.report(payload.modVersion, payload.sightings)),
);

const ProfilesLive = HttpApiBuilder.group(Api, "profiles", (handlers) =>
  handlers
    .handle("profile", ({ params, query }) => Profiles.profile(params.name, query.profile))
    .handle("container", ({ params }) => Profiles.container(params.snapshot, params.container)),
);

const AdminLive = HttpApiBuilder.group(Api, "admin", (handlers) =>
  handlers
    .handle("create", ({ payload }) => Contrib.createRequest(payload))
    .handle("requests", () => Contrib.reports)
    .handle("close", ({ params }) => Contrib.close(params.id))
    .handle("capture", ({ params }) => Contrib.captureOf(params.id))
    .handle("items", ({ query }) => Items.appearances({ ...query, limit: query.limit ?? Items.MAX_APPEARANCES_PAGE }))
    .handle("unknowns", () => Contrib.unknowns),
);

const web = HttpRouter.toWebHandler(
  HttpApiBuilder.layer(Api).pipe(
    Layer.provide([
      Health,
      Layer.provide(ContribLive, SignedInLive),
      Layer.provide(ProfilesLive, SignedInLive),
      Layer.provide(AdminLive, AgentLive),
    ]),
    // @keep The client sent what the errors quote, and a refusal is never logged, so they reach only that client.
    Layer.provide(HttpApiMiddleware.layerProblemDetails({ includeDetails: true })),
    Layer.provide(HttpServer.layerServices),
  ),
  { disableLogger: true },
);

/** @keep A failure no handler expected answers an empty 500 so nothing internal leaks, and goes to the Worker's log
 * instead. A body Effect could not read is the client's mistake, answered 400 or 413, so it is not logged. */
const logFailures = ErrorReporter.make(({ cause, error }) => {
  // @keep Effect lets the request's parse error die, so it is a defect here and not a failure.
  const unreadable = cause.reasons.every((reason) => {
    const original = Cause.isDieReason(reason) ? reason.defect : Cause.isFailReason(reason) ? reason.error : undefined;
    return HttpServerError.isHttpServerError(original) && original.reason instanceof HttpServerError.RequestParseError;
  });
  if (!unreadable) {
    // eslint-disable-next-line no-console -- the Worker's log is console.error, and this is the one place that writes it.
    console.error(error);
  }
});

/** @keep A capture of half an hour in a busy place is under this once gzipped. */
export const MAX_BODY = ByteSize.megabytes(50);

export interface Services {
  readonly contrib: Store["Service"];
  readonly items: ItemStore["Service"];
  readonly profiles: ProfileStore["Service"];
  readonly upstream: Upstream["Service"];
  readonly clock: Clock.Clock;
}

export const makeApp = (services: Services, callerOf: (request: Request) => Caller["Service"]) => {
  const context = Context.make(Store, services.contrib).pipe(
    Context.add(ItemStore, services.items),
    Context.add(ProfileStore, services.profiles),
    Context.add(Upstream, services.upstream),
    Context.add(Clock.Clock, services.clock),
    Context.add(ErrorReporter.CurrentErrorReporters, new Set([logFailures])),
    Context.add(HttpIncomingMessage.MaxBodySize, MAX_BODY),
  );
  return (request: Request) => web.handler(request, Context.add(context, Caller, callerOf(request)));
};
