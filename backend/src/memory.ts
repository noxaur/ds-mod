import { Clock, Effect } from "effect";

import type { Services } from "./app.ts";
import { memoryStore } from "./contrib/store.ts";
import { memoryItemStore } from "./items/store.ts";
import { memoryLimiter } from "./limiter.ts";
import { memoryProfileStore } from "./profiles/store.ts";

export const memoryServices = (overrides: Partial<Services> = {}): Services => ({
  contrib: memoryStore(),
  items: memoryItemStore(),
  profiles: memoryProfileStore(),
  upstream: { get: () => Effect.die("This test does not answer for dragsim.co") },
  limiter: memoryLimiter(),
  clock: Clock.Clock.defaultValue(),
  ...overrides,
});
