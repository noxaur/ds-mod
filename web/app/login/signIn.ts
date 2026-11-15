import { Effect } from "effect";
import { sessionCookie, sessionOf, verifyOneTimeToken } from "../backend";

/** Holds a redeemed session until the player confirms whose it is. Sent only to the login page. */
export const pendingCookie = "dsmod_pending";

const pendingMs = 5 * 60 * 1000;

interface CookieOptions {
  readonly expires: Date;
  readonly path: string;
  readonly httpOnly: true;
  readonly sameSite: "lax";
  readonly secure: boolean;
}

/** The part of Next's cookie store the login uses. */
export interface Jar {
  readonly get: (name: string) => { readonly value: string } | undefined;
  readonly set: (name: string, value: string, options: CookieOptions) => unknown;
}

const put = (jar: Jar, name: string, value: string, expires: Date, path: string) =>
  jar.set(name, value, {
    expires,
    path,
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
  });

/**
 * Finds whose sign-in is waiting to be confirmed, and answers their name or null. A token from the link is traded for
 * a pending session. A reloaded page has no token, or one that is spent, and finds the session it was already holding.
 */
export const begin = (token: string, jar: Jar) => {
  const pending = jar.get(pendingCookie)?.value;
  const held =
    pending === undefined ? Effect.succeed(null) : sessionOf(pending).pipe(Effect.map((s) => s?.name ?? null));
  if (token === "") {
    return held;
  }
  return verifyOneTimeToken(token).pipe(
    Effect.map(({ session, user }) => {
      const expires = new Date(Math.min(Date.now() + pendingMs, session.expiresAt.getTime()));
      put(jar, pendingCookie, session.token, expires, "/login");
      return user.name;
    }),
    Effect.catch(() => held),
  );
};

/** Ends the confirmation. Continue signs the browser in and answers the name, and anything else drops the session. */
export const finish = (choice: string, jar: Jar) => {
  const pending = jar.get(pendingCookie)?.value;
  put(jar, pendingCookie, "", new Date(0), "/login");
  if (choice !== "continue" || pending === undefined) {
    return Effect.succeed(null);
  }
  return sessionOf(pending).pipe(
    Effect.map((session) => {
      if (session === null) {
        return null;
      }
      put(jar, sessionCookie, pending, session.expiresAt, "/");
      return session.name;
    }),
  );
};
