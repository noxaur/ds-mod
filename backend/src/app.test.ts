import { readFileSync } from "node:fs";

import { expect, test } from "vite-plus/test";

import { openapi, openapiFile } from "../scripts/openapi.ts";

import { app } from "./app.ts";

test("healthz says the backend is up", async () => {
  expect((await app(new Request("http://backend/healthz"))).status).toBe(204);
});

test("api/openapi.json matches src/api.ts", () => {
  expect(readFileSync(openapiFile, "utf8"), "Run `pnpm openapi` in backend/ and commit the file.").toBe(openapi);
});
