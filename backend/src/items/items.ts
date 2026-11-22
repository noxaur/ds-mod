import { Effect } from "effect";

import type { ItemSighting } from "./model.ts";
import { safeTexture } from "../upstream/texture.ts";
import { type AppearanceQuery, ItemStore, type Observation } from "./store.ts";

/** @keep Any signed-in client can invent sightings. A real game has a few thousand items, so this is the backstop for
 * many accounts at once. */
export const MAX_ITEM_ROWS = 20_000;

// @keep One account's share of the table, so a single player cannot fill it.
export const MAX_PLAYER_ITEM_ROWS = 2_000;

export const MAX_SAMPLES = 5;

// @keep A sample larger than this is counted but not kept, because a client chooses what `data` holds.
export const MAX_SAMPLE_CHARS = 16_384;

const observe = (sighting: ItemSighting): Observation | null => {
  const texture = sighting.skullTexture ?? null;
  if (texture !== null && safeTexture(texture) === undefined) {
    return null;
  }
  return {
    entry: sighting.entry,
    skinId: sighting.skinId ?? "",
    texture,
    glint: sighting.glint,
    sample: JSON.stringify(sighting).length <= MAX_SAMPLE_CHARS ? sighting : null,
  };
};

export const report = Effect.fn(function* (userId: string, modVersion: string, sightings: readonly ItemSighting[]) {
  const observations = sightings.flatMap((sighting) => observe(sighting) ?? []);
  yield* (yield* ItemStore).record(userId, modVersion, observations, {
    maxRows: MAX_ITEM_ROWS,
    maxPlayerRows: MAX_PLAYER_ITEM_ROWS,
    maxSamples: MAX_SAMPLES,
  });
});

export const MAX_APPEARANCES_PAGE = 200;

export const appearances = (query: AppearanceQuery) => Effect.flatMap(ItemStore, (store) => store.appearances(query));
