import type { BetterAuthOptions } from "better-auth";
import { bearer } from "better-auth/plugins/bearer";
import { oneTimeToken } from "better-auth/plugins/one-time-token";

import { type HasJoined, minecraft, mojangHasJoined } from "./minecraft.ts";

export const signInOptions = (hasJoined: HasJoined = mojangHasJoined()) =>
  ({
    // @keep No other way in exists, so nobody can make an account Mojang did not vouch for.
    emailAndPassword: { enabled: false },
    // @keep Only a sign-in Mojang confirmed may set the name or link the account, so a player cannot pose as someone else.
    disabledPaths: ["/update-user", "/unlink-account"],
    // @keep A session is never extended, so a stolen token or cookie dies on its own. The mod signs in again unseen.
    session: { expiresIn: 60 * 60 * 24, disableSessionRefresh: true },
    // @keep A database error carries its query's parameters, and a session lookup's parameter is the session token.
    logger: {
      log: (level, message, ...args) => {
        // eslint-disable-next-line dragen/no-computed-console -- Better Auth names the level of each line it logs, and this is the one place that writes them to the Worker's log.
        console[level](message, ...args.map((arg: unknown) => (arg instanceof Error ? arg.name : typeof arg)));
      },
    },
    plugins: [
      minecraft(hasJoined),
      // @keep The mod has no cookie jar, so it sends its session as a bearer token.
      bearer(),
      oneTimeToken({ expiresIn: 1, storeToken: "hashed" }),
    ],
  }) satisfies BetterAuthOptions;
