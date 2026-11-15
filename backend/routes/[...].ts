import { defineHandler } from "void";

import { app } from "../src/app.ts";

// Void owns the Worker and its bindings, and the Effect API in src/ owns every path and method.
// The unnamed catch-all matches `/` too, which `[...path]` would not. GET also answers HEAD.
const handler = defineHandler((c) => app(c.req.raw));

export { handler as GET, handler as POST, handler as PUT, handler as PATCH, handler as DELETE, handler as OPTIONS };
