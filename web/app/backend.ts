import { Effect, Schema } from "effect";

const backendUrl = process.env["BACKEND_URL"] ?? "http://127.0.0.1:8787";

export const sessionCookie = "dsmod_session";

export const backendIsUp = Effect.tryPromise(() => fetch(`${backendUrl}/healthz`, { cache: "no-store" })).pipe(
  Effect.map((response) => response.ok),
  Effect.timeout("2 seconds"),
  Effect.orElseSucceed(() => false),
);

const Verified = Schema.Struct({
  session: Schema.Struct({ token: Schema.String, expiresAt: Schema.DateFromString }),
  user: Schema.Struct({ name: Schema.String }),
});

const Current = Schema.NullOr(
  Schema.Struct({
    session: Schema.Struct({ expiresAt: Schema.DateFromString }),
    user: Schema.Struct({ name: Schema.String }),
  }),
);

const call = (path: string, init: RequestInit) =>
  Effect.tryPromise(() => fetch(`${backendUrl}/api/auth/${path}`, { ...init, cache: "no-store" })).pipe(
    Effect.filterOrFail((response) => response.ok),
    Effect.flatMap((response) => Effect.tryPromise(() => response.json() as Promise<unknown>)),
    // Around the body too, which can stall after the headers arrived.
    Effect.timeout("5 seconds"),
  );

/** Trades a one-time token for the session it stands for. Fails when the backend refuses it. */
export const verifyOneTimeToken = (token: string) =>
  call("one-time-token/verify", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ token }),
  }).pipe(Effect.flatMap(Schema.decodeUnknownEffect(Verified)));

/** Whose session a token is and when it ends, or null when the backend does not know it. */
export const sessionOf = (sessionToken: string) =>
  call("get-session", { headers: { authorization: `Bearer ${sessionToken}` } }).pipe(
    Effect.flatMap(Schema.decodeUnknownEffect(Current)),
    Effect.map((current) => current && { name: current.user.name, expiresAt: current.session.expiresAt }),
    Effect.orElseSucceed(() => null),
  );
