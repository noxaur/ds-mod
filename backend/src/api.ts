import { HttpApi, HttpApiEndpoint, HttpApiGroup, OpenApi } from "effect/http-api";

/** Every REST endpoint. `api/openapi.json` is generated from this, so the mod and the website read the same contract. */
export const Api = HttpApi.make("dsmod")
  .add(
    HttpApiGroup.make("health").add(
      HttpApiEndpoint.get("healthz", "/healthz").annotate(OpenApi.Summary, "Reports that the backend is up."),
    ),
  )
  .annotateMerge(OpenApi.annotations({ title: "ds-mod backend", version: "0.1.0" }));
