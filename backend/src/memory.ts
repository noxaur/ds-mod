import { Clock, Effect } from "effect";

import type { Services } from "./app.ts";
import { memoryStore } from "./contrib/store.ts";
import { memoryItemStore } from "./items/store.ts";
import { memoryProfileStore } from "./profiles/store.ts";

/** Services that keep everything in memory, for the tests. dragsim.co is unreachable unless a test answers for it. */
export const memoryServices = (overrides: Partial<Services> = {}): Services => ({
  contrib: memoryStore(),
  items: memoryItemStore(),
  profiles: memoryProfileStore(),
  upstream: { get: () => Effect.die("This test does not answer for dragsim.co") },
  clock: Clock.Clock.defaultValue(),
  ...overrides,
});
