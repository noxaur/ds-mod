import { defineHandler } from "void";
import { getUser } from "void/auth";
import { env } from "void/env";

import { env as cloudflare } from "cloudflare:workers";
import { Clock, Effect } from "effect";

import { makeApp } from "../src/app.ts";
import type { Limiter, Rate } from "../src/limiter.ts";
import { contribStore, itemStore, profileStore, upstream } from "./_store.ts";

declare global {
  namespace Cloudflare {
    interface Env {
      RATE_PLAYER: RateLimit;
      RATE_PROFILE: RateLimit;
      RATE_ITEMS: RateLimit;
    }
  }
}

const bindings: Record<Rate, () => RateLimit> = {
  player: () => cloudflare.RATE_PLAYER,
  profile: () => cloudflare.RATE_PROFILE,
  items: () => cloudflare.RATE_ITEMS,
};

/** @keep Cloudflare counts each rate in each of its locations apart, so a player who moves between them gets a little
 * more. The test of every rule is against `memoryLimiter`, because Node runs no binding. */
const limiter: Limiter = {
  allow: (rate, userId) =>
    Effect.orDie(Effect.tryPromise(() => bindings[rate]().limit({ key: userId }))).pipe(
      Effect.map(({ success }) => success),
    ),
};

// @keep The unnamed catch-all matches `/` too, which `[...path]` would not. GET also answers HEAD.
const app = makeApp(
  {
    contrib: contribStore,
    items: itemStore,
    profiles: profileStore,
    upstream,
    limiter,
    clock: Clock.Clock.defaultValue(),
  },
  () => ({
    userId: getUser()?.id ?? null,
    adminToken: env.ADMIN_TOKEN,
  }),
);

const handler = defineHandler((c) => app(c.req.raw));

export { handler as GET, handler as POST, handler as PUT, handler as PATCH, handler as DELETE, handler as OPTIONS };
