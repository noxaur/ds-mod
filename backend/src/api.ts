import { ByteSize, Context, Schema } from "effect";
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
import { ItemAppearances, ItemSighting } from "./items/model.ts";
import type { Rate } from "./limiter.ts";
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

export class Caller extends Context.Service<
  Caller,
  { readonly userId: string | null; readonly adminToken: string | undefined }
>()("dsmod/Caller") {}

export class Player extends Context.Service<Player, { readonly userId: string }>()("dsmod/Player") {}

export class TooManyRequests extends Schema.TaggedError<TooManyRequests>()(
  "TooManyRequests",
  {},
  { httpApiStatus: 429, description: "The player called more often than a player can. A minute later they may again." },
) {}

/** @keep Void checks the bearer session token before the API runs, so the scheme is only for the contract. */
export class SignedIn extends HttpApiMiddleware.Service<SignedIn, { provides: Player }>()("dsmod/SignedIn", {
  error: [HttpApiError.Unauthorized, TooManyRequests],
  security: { session: HttpApiSecurity.bearer },
}) {}

export const MaxBody = Context.Reference<ByteSize.ByteSize>("dsmod/MaxBody", {
  defaultValue: () => ByteSize.kibibytes(64),
});

/** @keep A capture of half an hour in a busy place is under this once gzipped. */
export const MAX_CAPTURE = ByteSize.megabytes(50);

export class BodyLimit extends HttpApiMiddleware.Service<BodyLimit>()("dsmod/BodyLimit") {}

export const Limited = Context.Reference<Rate | null>("dsmod/Limited", { defaultValue: () => null });

export class Agent extends HttpApiMiddleware.Service<Agent>()("dsmod/Agent", {
  error: HttpApiError.Unauthorized,
  security: { adminToken: HttpApiSecurity.bearer },
}) {}

const id = { id: Schema.String };
const ModVersion = Schema.String.check(Schema.isMaxLength(64));
const Capture = Schema.Uint8Array.pipe(HttpApiSchema.asUint8Array());

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
        })
          .annotate(MaxBody, MAX_CAPTURE)
          .annotate(
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
        })
          .annotate(MaxBody, ByteSize.mebibytes(1))
          .annotate(Limited, "items")
          .annotate(OpenApi.Summary, "Keeps what the player saw of items, to draw them as the game does.")
          .annotate(OpenApi.Description, "The body is at most 1 MiB."),
      )
      .add(
        HttpApiEndpoint.post("report", "/v1/contrib/unknowns", {
          payload: Schema.Struct({
            modVersion: ModVersion,
            sightings: Schema.Array(Sighting).check(Schema.isMaxLength(100)),
          }),
        })
          .annotate(MaxBody, ByteSize.mebibytes(2))
          .annotate(OpenApi.Summary, "Counts what the mod saw and had no handling for.")
          .annotate(OpenApi.Description, "The body is at most 2 MiB."),
      )
      .middleware(SignedIn)
      .annotate(
        OpenApi.Description,
        "What the mod calls, as the player its session names. A player who calls too often is answered 429.",
      ),
  )
  .add(
    HttpApiGroup.make("profiles")
      .add(
        HttpApiEndpoint.get("profile", "/v1/players/:name/profile", {
          params: { name: PlayerName },
          query: { profile: Schema.optionalKey(ProfileId) },
          success: Profile,
          error: [PlayerNotFound, ProfileNotFound, UpstreamUnavailable],
        })
          .annotate(Limited, "profile")
          .annotate(
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
      .add(
        HttpApiEndpoint.get("containers", "/v1/snapshots/:snapshot/containers", {
          params: { snapshot: Schema.String },
          success: Schema.Array(Container),
          error: [SnapshotExpired, UpstreamUnavailable],
        })
          .annotate(OpenApi.Summary, "Every container of a profile, as it was when the snapshot was taken.")
          .annotate(
            OpenApi.Description,
            "In the order the profile lists them. The mod's item overview reads them all at once, so it asks once instead of once per container.",
          ),
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
        HttpApiEndpoint.get("items", "/v1/admin/items", {
          query: {
            entry: Schema.optionalKey(Schema.String),
            after: Schema.optionalKey(Schema.String),
            limit: Schema.optionalKey(
              Schema.FiniteFromString.check(Schema.isInt(), Schema.isBetween({ minimum: 1, maximum: 200 })),
            ),
          },
          success: ItemAppearances,
        })
          .annotate(OpenApi.Summary, "What players saw of items, a page at a time.")
          .annotate(
            OpenApi.Description,
            "With `entry`, that one entry with the samples to test the rebuilt tooltip against. Without it, up to `limit` entries (200 at most, and by default) in order, with no samples, and `next` is the `after` for the page after. Samples are only in the one-entry form so a reply stays small.",
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
  .middleware(BodyLimit)
  .middleware(HttpApiMiddleware.ProblemDetails)
  .annotate(HttpApi.PayloadParseOptions, { onExcessProperty: "error" })
  .annotateMerge(OpenApi.annotations({ title: "ds-mod backend", version: "0.1.0" }));
