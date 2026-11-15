import { defineHandler } from "void";
import { getUser } from "void/auth";
import { env } from "void/env";

import { makeApp } from "../src/app.ts";
import { contribStore } from "./_store.ts";

// Void owns the Worker, its bindings and the sign-in, and the Effect API in src/ owns every path and method. All this
// file adds is who Void says is signed in and what the admin token is.
// The unnamed catch-all matches `/` too, which `[...path]` would not. GET also answers HEAD.
const app = makeApp(contribStore, () => ({ userId: getUser()?.id ?? null, adminToken: env.ADMIN_TOKEN }));

const handler = defineHandler((c) => app(c.req.raw));

export { handler as GET, handler as POST, handler as PUT, handler as PATCH, handler as DELETE, handler as OPTIONS };
