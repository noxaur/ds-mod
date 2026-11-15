import { Context, Schema } from "effect";
import {
  HttpApi,
  HttpApiEndpoint,
  HttpApiError,
  HttpApiGroup,
  HttpApiMiddleware,
  HttpApiSchema,
  HttpApiSecurity,
  OpenApi,
} from "effect/http-api";

import { Offer, Request, RequestReport, RequestSpec, Sighting, Unknown } from "./contrib/model.ts";

/**
 * Who a request is from, as the entry point that verified it says: the player whose session Void found, and the
 * admin token an agent must match. `makeApp` adds it to each request's context, where a client cannot reach.
 */
export class Caller extends Context.Service<
  Caller,
  { readonly userId: string | null; readonly adminToken: string | undefined }
>()("dsmod/Caller") {}

/** The signed-in player a contrib handler acts for. */
export class Player extends Context.Service<Player, { readonly userId: string }>()("dsmod/Player") {}

/** Lets a request through only when Void's session names a player, and gives the handler that player. */
export class SignedIn extends HttpApiMiddleware.Service<SignedIn, { provides: Player }>()("dsmod/SignedIn", {
  error: HttpApiError.Unauthorized,
}) {}

/** Lets a request through only when it carries the admin token, which only an agent's `dsctl` has. */
export class Agent extends HttpApiMiddleware.Service<Agent>()("dsmod/Agent", {
  error: HttpApiError.Unauthorized,
  security: { adminToken: HttpApiSecurity.bearer },
}) {}

const id = { id: Schema.String };
// A version is a few characters, and it is stored with every run, so a client may not send a megabyte as one.
const ModVersion = Schema.String.check(Schema.isMaxLength(64));
const Capture = Schema.Uint8Array.pipe(HttpApiSchema.asUint8Array());

/** Every REST endpoint but sign-in. `api/openapi.json` is generated from this, so the mod and the website read the same contract. */
export const Api = HttpApi.make("dsmod")
  .add(
    HttpApiGroup.make("health").add(
      HttpApiEndpoint.get("healthz", "/healthz").annotate(OpenApi.Summary, "Reports that the backend is up."),
    ),
  )
  .add(
    HttpApiGroup.make("contrib")
      .add(
        HttpApiEndpoint.get("offers", "/v1/contrib/requests", { success: Schema.Array(Offer) }).annotate(
          OpenApi.Summary,
          "The open requests the player has neither recorded nor skipped.",
        ),
      )
      .add(
        HttpApiEndpoint.post("skip", "/v1/contrib/requests/:id/skip", {
          params: id,
          error: HttpApiError.NotFound,
        }).annotate(OpenApi.Summary, "Stops offering a request to the player."),
      )
      .add(
        HttpApiEndpoint.post("submit", "/v1/contrib/requests/:id/submissions", {
          params: id,
          query: { modVersion: ModVersion },
          payload: Capture,
          success: Schema.Struct(id),
          error: [HttpApiError.NotFound, HttpApiError.Conflict, HttpApiError.BadRequest],
        }).annotate(
          OpenApi.Description,
          "Keeps the player's run of a request: the capture file, gzipped, at most 10 MB (413 above it, as for any body). A player has one run per request, and a closed or full request takes none (409).",
        ),
      )
      .add(
        HttpApiEndpoint.post("report", "/v1/contrib/unknowns", {
          payload: Schema.Struct({
            modVersion: ModVersion,
            sightings: Schema.Array(Sighting).check(Schema.isMaxLength(100)),
          }),
        }).annotate(OpenApi.Summary, "Counts what the mod saw and had no handling for."),
      )
      .middleware(SignedIn)
      .annotate(OpenApi.Description, "What the mod calls, as the player its session names."),
  )
  .add(
    HttpApiGroup.make("admin")
      .add(
        HttpApiEndpoint.post("create", "/v1/admin/requests", { payload: RequestSpec, success: Request }).annotate(
          OpenApi.Summary,
          "Publishes a recording request.",
        ),
      )
      .add(
        HttpApiEndpoint.get("requests", "/v1/admin/requests", { success: Schema.Array(RequestReport) }).annotate(
          OpenApi.Summary,
          "Every request with its runs, a letter in place of each player.",
        ),
      )
      .add(
        HttpApiEndpoint.post("close", "/v1/admin/requests/:id/close", {
          params: id,
          error: HttpApiError.NotFound,
        }).annotate(OpenApi.Summary, "Stops offering a request to anyone."),
      )
      .add(
        HttpApiEndpoint.get("capture", "/v1/admin/submissions/:id/capture", {
          params: id,
          success: Capture,
          error: HttpApiError.NotFound,
        }).annotate(OpenApi.Summary, "A run's capture file, gzipped."),
      )
      .add(
        HttpApiEndpoint.get("unknowns", "/v1/admin/unknowns", { success: Schema.Array(Unknown) }).annotate(
          OpenApi.Summary,
          "What the mod saw and had no handling for, with how often.",
        ),
      )
      .middleware(Agent)
      .annotate(OpenApi.Description, "What an agent calls through dsctl, with the admin token."),
  )
  // The mod parses a brief strictly, so a key it does not know, such as `secs` for `seconds`, is refused here
  // instead of being dropped and costing a step its timer.
  .annotate(HttpApi.PayloadParseOptions, { onExcessProperty: "error" })
  .annotateMerge(OpenApi.annotations({ title: "ds-mod backend", version: "0.1.0" }));
