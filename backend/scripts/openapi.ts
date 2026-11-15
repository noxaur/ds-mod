import { writeFileSync } from "node:fs";
import { join } from "node:path";

import { OpenApi } from "effect/http-api";

import { Api } from "../src/api.ts";

export const openapi = `${JSON.stringify(OpenApi.fromApi(Api), null, 2)}\n`;
export const openapiFile = join(import.meta.dirname, "../../api/openapi.json");

if (import.meta.main) {
  writeFileSync(openapiFile, openapi);
}
