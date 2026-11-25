import { Effect, Schema } from "effect";

import { trustedKeyHeader } from "../shared/website";
import { backendUrl, trustedKey } from "./env";

export const sessionCookie = "dragen_session";

export const backendIsUp = Effect.tryPromise(() => fetch(`${backendUrl}/healthz`, { cache: "no-store" })).pipe(
  Effect.map((response) => response.ok),
  Effect.timeout("2 seconds"),
  Effect.orElseSucceed(() => false),
);

const User = Schema.Struct({
  id: Schema.String,
  name: Schema.String,
  image: Schema.optionalKey(Schema.NullOr(Schema.String)),
});

const Verified = Schema.Struct({
  session: Schema.Struct({ token: Schema.String, expiresAt: Schema.DateFromString }),
  user: User,
});

const Current = Schema.NullOr(
  Schema.Struct({ session: Schema.Struct({ expiresAt: Schema.DateFromString }), user: User }),
);

const withTrustedKey = (headers: Record<string, string>) => {
  const key = trustedKey();
  return key === undefined || key === "" ? headers : { ...headers, [trustedKeyHeader]: key };
};

const call = (path: string, init: RequestInit & { headers: Record<string, string> }) =>
  Effect.tryPromise(() =>
    fetch(`${backendUrl}/api/auth/${path}`, { ...init, headers: withTrustedKey(init.headers), cache: "no-store" }),
  ).pipe(
    Effect.filterOrFail((response) => response.ok),
    Effect.flatMap((response) => Effect.tryPromise(() => response.json() as Promise<unknown>)),
    // @keep The body can stall after the headers arrived, so the timeout covers it too.
    Effect.timeout("5 seconds"),
  );

export const verifyOneTimeToken = (token: string) =>
  call("one-time-token/verify", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ token }),
  }).pipe(Effect.flatMap(Schema.decodeUnknownEffect(Verified)));

export const sessionOf = (sessionToken: string) =>
  call("get-session", { headers: { authorization: `Bearer ${sessionToken}` } }).pipe(
    Effect.flatMap(Schema.decodeUnknownEffect(Current)),
    Effect.map((current) => current && { ...current.user, expiresAt: current.session.expiresAt }),
  );
