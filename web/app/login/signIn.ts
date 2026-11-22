import { Effect } from "effect";
import { sessionCookie, sessionOf, verifyOneTimeToken } from "../backend";
import { inProduction } from "../env";

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

/** The account a login page asks about: its user id, which the answer must match, and the name it shows. */
export interface Asked {
  readonly user: string;
  readonly name: string;
  readonly skin: string | null;
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

/**
 * Finds whose sign-in is waiting to be confirmed, or null. A token from the link is traded for a pending session.
 * A reloaded page has no token, or one that is spent, and finds the session it was already holding.
 */
export const begin = (token: string, jar: Jar): Effect.Effect<Asked | null> => {
  const pending = jar.get(pendingCookie)?.value;
  const held =
    pending === undefined
      ? Effect.succeed(null)
      : sessionOf(pending).pipe(
          Effect.map((s) => s && { user: s.id, name: s.name, skin: s.image ?? null }),
          Effect.orElseSucceed(() => null),
        );
  if (token === "") {
    return held;
  }
  return verifyOneTimeToken(token).pipe(
    Effect.map(({ session, user }) => {
      const expires = new Date(Math.min(Date.now() + pendingMs, session.expiresAt.getTime()));
      put(jar, pendingCookie, session.token, expires, "/login");
      return { user: user.id, name: user.name, skin: user.image ?? null };
    }),
    Effect.catch(() => held),
  );
};

/**
 * Ends the confirmation of [user]. Continue signs the browser in and answers the name, and anything else drops the
 * session. A pending session that is someone else's, because another tab redeemed a second link, signs nobody in.
 * When the backend cannot say whose the session is, it stays pending and this fails, so the player can try again.
 */
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
