import { defineHandler } from "void";

import { app } from "../src/app.ts";

// Void owns the Worker and its bindings, and the Effect API in src/ owns every route.
const handler = defineHandler((c) => app(c.req.raw));

export const GET = handler;
export const POST = handler;
export const PUT = handler;
export const PATCH = handler;
export const DELETE = handler;
