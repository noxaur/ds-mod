import { Clock, Context, Effect, Schema } from "effect";

/** @keep Cloudflare's KV takes one write per key a second, so two lookups of one player at the same moment refuse the second. */
export class WriteRefused extends Schema.TaggedError<WriteRefused>()("WriteRefused", {}) {}

export class ProfileStore extends Context.Service<
  ProfileStore,
  {
    readonly get: (key: string) => Effect.Effect<unknown>;
    readonly put: (key: string, value: unknown, ttlSeconds: number) => Effect.Effect<void, WriteRefused>;
  }
>()("dsmod/profiles/ProfileStore") {}

export const memoryProfileStore = (): ProfileStore["Service"] => {
  const values = new Map<string, { value: unknown; until: number }>();
  return {
    get: (key) =>
      Effect.map(Clock.currentTimeMillis, (now) => {
        const entry = values.get(key);
        return entry !== undefined && now < entry.until ? entry.value : null;
      }),
    put: (key, value, ttlSeconds) =>
      Effect.map(Clock.currentTimeMillis, (now) => void values.set(key, { value, until: now + ttlSeconds * 1000 })),
  };
};
