import { Effect } from "effect";

import type { ItemSighting } from "./model.ts";
import { ItemStore } from "./store.ts";

export const report = Effect.fn(function* (userId: string, modVersion: string, sightings: readonly ItemSighting[]) {
  yield* (yield* ItemStore).insertSightings(userId, modVersion, sightings);
});

export const appearances = Effect.flatMap(ItemStore, (store) => store.appearances);
