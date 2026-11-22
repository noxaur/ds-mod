import type { Effect } from "effect";
import { Context, Schema } from "effect";

/** @keep Cloudflare's KV takes one write per key a second, so two lookups of one player at the same moment refuse the second. */
export class WriteRefused extends Schema.TaggedError<WriteRefused>()("WriteRefused", {}) {}

export class ProfileStore extends Context.Service<
  ProfileStore,
  {
    readonly get: (key: string) => Effect.Effect<unknown>;
    readonly put: (key: string, value: unknown, ttlSeconds: number) => Effect.Effect<void, WriteRefused>;
  }
>()("dsmod/profiles/ProfileStore") {}
