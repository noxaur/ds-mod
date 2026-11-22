import { Effect } from "effect";
import { sessionCookie, sessionOf, verifyOneTimeToken } from "../backend";
import { inProduction } from "../env";

export const pendingCookie = "dsmod_pending";

const pendingMs = 5 * 60 * 1000;

interface CookieOptions {
  readonly expires: Date;
  readonly path: string;
  readonly httpOnly: true;
  readonly sameSite: "lax";
  readonly secure: boolean;
}

export interface Jar {
  readonly get: (name: string) => { readonly value: string } | undefined;
  readonly set: (name: string, value: string, options: CookieOptions) => unknown;
}

export interface Asked {
  readonly user: string;
  readonly name: string;
}

const put = (jar: Jar, name: string, value: string, expires: Date, path: string) =>
  jar.set(name, value, {
    expires,
    path,
    httpOnly: true,
    sameSite: "lax",
    secure: inProduction(),
  });

const dropPending = (jar: Jar) => put(jar, pendingCookie, "", new Date(0), "/login");

export const begin = (token: string, jar: Jar): Effect.Effect<Asked | null> => {
  const pending = jar.get(pendingCookie)?.value;
  const held =
    pending === undefined
      ? Effect.succeed(null)
      : sessionOf(pending).pipe(
          Effect.map((s) => s && { user: s.id, name: s.name }),
          Effect.orElseSucceed(() => null),
        );
  if (token === "") {
    return held;
  }
  return verifyOneTimeToken(token).pipe(
    Effect.map(({ session, user }) => {
      const expires = new Date(Math.min(Date.now() + pendingMs, session.expiresAt.getTime()));
      put(jar, pendingCookie, session.token, expires, "/login");
      return { user: user.id, name: user.name };
    }),
    Effect.catch(() => held),
  );
};

export const finish = (choice: string, user: string, jar: Jar) => {
  const pending = jar.get(pendingCookie)?.value;
  if (choice !== "continue" || pending === undefined) {
    dropPending(jar);
    return Effect.succeed(null);
  }
  return sessionOf(pending).pipe(
    Effect.map((session) => {
      if (session === null) {
        dropPending(jar);
        return null;
      }
      if (session.id !== user) {
        return null;
      }
      dropPending(jar);
      put(jar, sessionCookie, pending, session.expiresAt, "/");
      return session.name;
    }),
  );
};
