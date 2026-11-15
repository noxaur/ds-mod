import { Schema } from "effect";
import { HttpApi, HttpApiEndpoint, HttpApiError, HttpApiGroup, HttpApiSchema } from "effect/http-api";

import { Offer, Request, RequestReport, RequestSpec, Sighting, Unknown } from "./contrib/model.ts";

const id = { id: Schema.String };
// A version is a few characters, and it is stored with every run, so a client may not send a megabyte as one.
const ModVersion = Schema.String.check(Schema.isMaxLength(64));
const Capture = Schema.Uint8Array.pipe(HttpApiSchema.asUint8Array());
const denied = [HttpApiError.Unauthorized, HttpApiError.NotFound, HttpApiError.Conflict, HttpApiError.BadRequest];

/** Every REST endpoint. `api/openapi.json` is generated from this, so the mod and the website read the same contract. */
export const Api = HttpApi.make("dsmod")
  .add(HttpApiGroup.make("health").add(HttpApiEndpoint.get("healthz", "/healthz")))
  .add(
    // What the mod calls, as the signed-in player.
    HttpApiGroup.make("contrib")
      .add(HttpApiEndpoint.get("offers", "/v1/contrib/requests", { success: Schema.Array(Offer), error: denied }))
      .add(HttpApiEndpoint.post("skip", "/v1/contrib/requests/:id/skip", { params: id, error: denied }))
      .add(
        HttpApiEndpoint.post("submit", "/v1/contrib/requests/:id/submissions", {
          params: id,
          query: { modVersion: ModVersion },
          // The capture file, gzipped.
          payload: Capture,
          success: Schema.Struct(id),
          error: denied,
        }),
      )
      .add(
        HttpApiEndpoint.post("report", "/v1/contrib/unknowns", {
          payload: Schema.Struct({ modVersion: ModVersion, sightings: Schema.Array(Sighting) }),
          error: denied,
        }),
      ),
  )
  .add(
    // What an agent calls through dsctl, with the admin token.
    HttpApiGroup.make("admin")
      .add(
        HttpApiEndpoint.post("create", "/v1/admin/requests", { payload: RequestSpec, success: Request, error: denied }),
      )
      .add(
        HttpApiEndpoint.get("requests", "/v1/admin/requests", { success: Schema.Array(RequestReport), error: denied }),
      )
      .add(HttpApiEndpoint.post("close", "/v1/admin/requests/:id/close", { params: id, error: denied }))
      .add(
        HttpApiEndpoint.get("capture", "/v1/admin/submissions/:id/capture", {
          params: id,
          success: Capture,
          error: denied,
        }),
      )
      .add(HttpApiEndpoint.get("unknowns", "/v1/admin/unknowns", { success: Schema.Array(Unknown), error: denied })),
  );
