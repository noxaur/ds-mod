import type { BetterAuthOptions } from "better-auth";
import { bearer } from "better-auth/plugins/bearer";
import { oneTimeToken } from "better-auth/plugins/one-time-token";

import { type HasJoined, minecraft, mojangHasJoined } from "./minecraft.ts";

/** How Better Auth signs a player in. `auth.ts` adds Void's database and secret, and the tests add their own. */
export const signInOptions = (hasJoined: HasJoined = mojangHasJoined) =>
  ({
    // A player is their Minecraft account. No other way in exists, so nobody can make an account Mojang did not vouch for.
    emailAndPassword: { enabled: false },
    // Only a sign-in Mojang confirmed may set the name the website asks the player to confirm, and only it may link
    // the account, so a player can neither pose as someone else nor cut their account loose from its UUID.
    disabledPaths: ["/update-user", "/unlink-account"],
    // A session ends 24 hours after it began and is never extended, so a stolen token or cookie dies on its own.
    // The mod proves itself to Mojang again without the player noticing.
    session: { expiresIn: 60 * 60 * 24, disableSessionRefresh: true },
    // A database error carries its query's parameters, and a session lookup's parameter is the session token.
    logger: {
      log: (level, message, ...args) => {
        console[level](message, ...args.map((arg: unknown) => (arg instanceof Error ? arg.name : typeof arg)));
      },
    },
    plugins: [
      minecraft(hasJoined),
      // The mod has no cookie jar, so it sends its session as a bearer token.
      bearer(),
      // `/dsmod login` trades the mod's session for a link that signs a browser in once, within a minute.
      oneTimeToken({ expiresIn: 1, storeToken: "hashed" }),
    ],
  }) satisfies BetterAuthOptions;
