import { Context, Effect, Schema } from "effect";

/**
 * The store would not keep a value. Cloudflare's KV takes one write per key a second, so two lookups of one player at
 * the same moment refuse the second write, though both have the answer.
 */
export class WriteRefused extends Schema.TaggedError<WriteRefused>()("WriteRefused", {}) {}

/** Copies of what dragsim.co sent, and the snapshots made from them, which expire by themselves. */
export class ProfileStore extends Context.Service<
  ProfileStore,
  {
    readonly get: (key: string) => Effect.Effect<unknown>;
    readonly put: (key: string, value: unknown, ttlSeconds: number) => Effect.Effect<void, WriteRefused>;
  }
>()("dsmod/profiles/ProfileStore") {}

/** A store that forgets everything when the process ends, for the tests. It does not expire, which a test does not need. */
export const memoryProfileStore = (): ProfileStore["Service"] => {
  const values = new Map<string, unknown>();
  return {
    get: (key) => Effect.sync(() => values.get(key) ?? null),
    put: (key, value) => Effect.sync(() => void values.set(key, value)),
  };
};
