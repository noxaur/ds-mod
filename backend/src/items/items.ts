import { Effect } from "effect";

import type { ItemSighting } from "./model.ts";
import { safeTexture } from "./render/policy.ts";
import { type AppearanceQuery, ItemStore, type Observation } from "./store.ts";

/**
 * Any signed-in client can invent sightings, so the table stops taking new items here. A real game has a few
 * thousand different items, and a row is one player's view of one. This is the backstop for many accounts at once.
 */
export const MAX_ITEM_ROWS = 20_000;

/** One account's share of the table, so a single player cannot fill it. A real player sees fewer different items. */
export const MAX_PLAYER_ITEM_ROWS = 2_000;

/** Examples kept per item, one per player, for an agent to test the rebuilt tooltip against. */
export const MAX_SAMPLES = 5;

/** A sample larger than this is counted but not kept, because a client chooses what `data` holds. */
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

/** Keeps what a player saw. A sighting with a texture from anywhere but Mojang is dropped whole. */
export const report = Effect.fn(function* (userId: string, modVersion: string, sightings: readonly ItemSighting[]) {
  const observations = sightings.flatMap((sighting) => observe(sighting) ?? []);
  yield* (yield* ItemStore).record(userId, modVersion, observations, {
    maxRows: MAX_ITEM_ROWS,
    maxPlayerRows: MAX_PLAYER_ITEM_ROWS,
    maxSamples: MAX_SAMPLES,
  });
});

/** Most entries in one page of the agent's view. */
export const MAX_APPEARANCES_PAGE = 200;

export const appearances = (query: AppearanceQuery) => Effect.flatMap(ItemStore, (store) => store.appearances(query));
