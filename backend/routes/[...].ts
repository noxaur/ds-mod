import { defineHandler } from "void";
import { getUser } from "void/auth";
import { env } from "void/env";

import { Clock } from "effect";

import { makeApp } from "../src/app.ts";
import { contribStore, itemStore, profileStore, upstream } from "./_store.ts";

// @keep The unnamed catch-all matches `/` too, which `[...path]` would not. GET also answers HEAD.
const app = makeApp(
  { contrib: contribStore, items: itemStore, profiles: profileStore, upstream, clock: Clock.Clock.defaultValue() },
  () => ({
    userId: getUser()?.id ?? null,
    adminToken: env.ADMIN_TOKEN,
  }),
);

const handler = defineHandler((c) => app(c.req.raw));

export { handler as GET, handler as POST, handler as PUT, handler as PATCH, handler as DELETE, handler as OPTIONS };
