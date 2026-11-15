import { defineHandler } from "void";
import { getUser } from "void/auth";
import { env } from "void/env";

import { makeApp } from "../src/app.ts";
import { d1Store } from "../store.ts";

// Void owns the Worker, its bindings and the sign-in, and the Effect API in src/ owns every route. All this file
// adds is who Void says is signed in and what the admin token is.
const app = makeApp(d1Store, () => ({ userId: getUser()?.id ?? null, adminToken: env.ADMIN_TOKEN }));

const handler = defineHandler((c) => app(c.req.raw));

export const GET = handler;
export const POST = handler;
export const PUT = handler;
export const PATCH = handler;
export const DELETE = handler;
