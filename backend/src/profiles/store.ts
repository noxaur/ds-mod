import { Context, Effect } from "effect";

/** Copies of what dragsim.co sent, and the snapshots made from them, which expire by themselves. */
export class ProfileStore extends Context.Service<
  ProfileStore,
  {
    readonly get: (key: string) => Effect.Effect<unknown>;
    readonly put: (key: string, value: unknown, ttlSeconds: number) => Effect.Effect<void>;
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
