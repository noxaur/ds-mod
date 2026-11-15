import { ByteSize, Cause, Context, Effect, ErrorReporter, Layer, Option, Redacted } from "effect";
import { HttpIncomingMessage, HttpRouter, HttpServer, HttpServerError } from "effect/http";
import { HttpApiBuilder, HttpApiError, HttpApiMiddleware } from "effect/http-api";

import { Agent, Api, Caller, Player, SignedIn } from "./api.ts";
import * as Contrib from "./contrib/contrib.ts";
import { Store } from "./contrib/store.ts";

/** Compares in the same time whether or not the first characters match, so the admin token cannot be guessed a letter at a time. */
function same(given: string, expected: string) {
  let difference = given.length ^ expected.length;
  for (let index = 0; index < given.length; index++) {
    difference |= given.charCodeAt(index) ^ expected.charCodeAt(index % Math.max(expected.length, 1));
  }
  return difference === 0;
}

// A middleware may only require services the app is built with, and Caller comes with each request, so it is read as
// an option. A request without one has no caller and is refused.
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

// With no admin token configured, nobody is an agent.
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
    .handle("report", ({ payload }) => Contrib.report(payload.modVersion, payload.sightings)),
);

const AdminLive = HttpApiBuilder.group(Api, "admin", (handlers) =>
  handlers
    .handle("create", ({ payload }) => Contrib.createRequest(payload))
    .handle("requests", () => Contrib.reports)
    .handle("close", ({ params }) => Contrib.close(params.id))
    .handle("capture", ({ params }) => Contrib.captureOf(params.id))
    .handle("unknowns", () => Contrib.unknowns),
);

const web = HttpRouter.toWebHandler(
  HttpApiBuilder.layer(Api).pipe(
    Layer.provide([Health, Layer.provide(ContribLive, SignedInLive), Layer.provide(AdminLive, AgentLive)]),
    // The client sent what the errors quote, and a refusal is never logged, so they reach only that client.
    Layer.provide(HttpApiMiddleware.layerProblemDetails({ includeDetails: true })),
    Layer.provide(HttpServer.layerServices),
  ),
  { disableLogger: true },
);

// A failure no handler expected, such as a database that is down, answers an empty 500 so nothing internal leaks.
// The cause goes to the Worker's log instead: the dev server's terminal, or the Worker's logs in Cloudflare. Effect
// reports a body it could not read too, but that is the client's mistake, answered 400 or 413, like the refusals
// a handler declares.
const logFailures = ErrorReporter.make(({ cause, error }) => {
  // Effect lets the request's parse error die, so it is a defect here and not a failure.
  const unreadable = cause.reasons.every((reason) => {
    const original = Cause.isDieReason(reason) ? reason.defect : Cause.isFailReason(reason) ? reason.error : undefined;
    return HttpServerError.isHttpServerError(original) && original.reason instanceof HttpServerError.RequestParseError;
  });
  if (!unreadable) {
    // eslint-disable-next-line no-console -- the Worker's log is console.error, and this is the one place that writes it.
    console.error(error);
  }
});

/**
 * The largest body any endpoint reads, a capture of half an hour in a busy place being under it once gzipped. A body is read
 * against it as it arrives, so a client cannot make the Worker hold more than this, whatever it claims to send.
 */
export const MAX_BODY = ByteSize.megabytes(50);

/**
 * The API as a function from request to response, keeping its rows and files in [store]. [callerOf] says who a request
 * is from, and is the only thing that can.
 */
export const makeApp = (store: Store["Service"], callerOf: (request: Request) => Caller["Service"]) => {
  const context = Context.make(Store, store).pipe(
    Context.add(ErrorReporter.CurrentErrorReporters, new Set([logFailures])),
    Context.add(HttpIncomingMessage.MaxBodySize, MAX_BODY),
  );
  return (request: Request) => web.handler(request, Context.add(context, Caller, callerOf(request)));
};
