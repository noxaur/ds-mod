import { HttpApi, HttpApiEndpoint, HttpApiGroup } from "effect/http-api";

/** Every REST endpoint. `api/openapi.json` is generated from this, so the mod and the website read the same contract. */
export const Api = HttpApi.make("dsmod").add(
  HttpApiGroup.make("health").add(HttpApiEndpoint.get("healthz", "/healthz")),
);
