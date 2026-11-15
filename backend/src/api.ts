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

import { Offer, RecordingRequest, RequestReport, RequestSpec, Sighting, Unknown } from "./contrib/model.ts";
import { ItemAppearance, ItemSighting } from "./items/model.ts";
import {
  Container,
  ContainerNotFound,
  PlayerName,
  PlayerNotFound,
  Profile,
  ProfileId,
  ProfileNotFound,
  SnapshotExpired,
  UpstreamUnavailable,
} from "./profiles/model.ts";

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

/**
 * Lets a request through only when Void's session names a player, and gives the handler that player. The mod sends
 * the session token sign-in gave it as a bearer token. Void checks it before the API runs, so the scheme is here for
 * the contract and the middleware reads the player Void found.
 */
export class SignedIn extends HttpApiMiddleware.Service<SignedIn, { provides: Player }>()("dsmod/SignedIn", {
  error: HttpApiError.Unauthorized,
  security: { session: HttpApiSecurity.bearer },
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
          "Keeps the player's run of a request: the capture file, gzipped, at most 50 MB (413 above it, as for any body). A player has one run per request. A filled request still takes a run from a player who was recording when it filled, and a closed one, or one with twice the wanted number of runs, takes none (409).",
        ),
      )
      .add(
        HttpApiEndpoint.post("items", "/v1/contrib/items", {
          payload: Schema.Struct({
            modVersion: ModVersion,
            items: Schema.Array(ItemSighting).check(Schema.isMaxLength(50)),
          }),
        }).annotate(OpenApi.Summary, "Keeps what the player saw of items, to draw them as the game does."),
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
    HttpApiGroup.make("profiles")
      .add(
        HttpApiEndpoint.get("profile", "/v1/players/:name/profile", {
          params: { name: PlayerName },
          query: { profile: Schema.optionalKey(ProfileId) },
          success: Profile,
          error: [PlayerNotFound, ProfileNotFound, UpstreamUnavailable],
        }).annotate(
          OpenApi.Description,
          "A player's profile as the overview draws it: the one they play now, or the one `profile` names. The backend keeps a copy for a short while, so asking again soon does not reach dragsim.co.",
        ),
      )
      .add(
        HttpApiEndpoint.get("container", "/v1/snapshots/:snapshot/containers/:container", {
          params: { snapshot: Schema.String, container: Schema.String },
          success: Container,
          error: [ContainerNotFound, SnapshotExpired, UpstreamUnavailable],
        }).annotate(OpenApi.Summary, "One container of a profile, as it was when the snapshot was taken."),
      )
      .middleware(SignedIn)
      .annotate(
        OpenApi.Description,
        "What the mod and the website show of a player, as the player their session names.",
      ),
  )
  .add(
    HttpApiGroup.make("admin")
      .add(
        HttpApiEndpoint.post("create", "/v1/admin/requests", {
          payload: RequestSpec,
          success: RecordingRequest,
        }).annotate(OpenApi.Summary, "Publishes a recording request."),
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
        HttpApiEndpoint.get("items", "/v1/admin/items", { success: Schema.Array(ItemAppearance) }).annotate(
          OpenApi.Summary,
          "What players saw of each item, with samples to test the rebuilt tooltips against.",
        ),
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
  // A body, path or query that does not decode is a 400 whose `errors` say where and what was expected, so an agent
  // can fix its request. The mod parses a brief strictly, so a key it does not know, such as `secs` for `seconds`, is
  // refused here instead of being dropped and costing a step its timer.
  .middleware(HttpApiMiddleware.ProblemDetails)
  .annotate(HttpApi.PayloadParseOptions, { onExcessProperty: "error" })
  .annotateMerge(OpenApi.annotations({ title: "ds-mod backend", version: "0.1.0" }));
