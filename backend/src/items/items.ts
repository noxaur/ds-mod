import { Effect, Option, Result, Schema } from "effect";
import { decodeString } from "effect/encoding/Base64";

import type { ItemSighting } from "./model.ts";
import { ItemStore, type Observation } from "./store.ts";

/**
 * Any signed-in client can invent sightings, so the table stops taking new items here. A real game has a few
 * thousand different items, and a row is one player's view of one.
 */
export const MAX_ITEM_ROWS = 20_000;

/** Examples kept per item, one per player, for an agent to test the rebuilt tooltip against. */
export const MAX_SAMPLES = 5;

/** A sample larger than this is counted but not kept, because a client chooses what `data` holds. */
export const MAX_SAMPLE_CHARS = 16_384;

const decodeTextures = Schema.decodeUnknownOption(
  Schema.fromJsonString(
    Schema.Struct({ textures: Schema.Record(Schema.String, Schema.Struct({ url: Schema.String })) }),
  ),
);

/**
 * Whether a skull texture is the base64 JSON the game reads, with every url on `textures.minecraft.net`. A texture
 * anywhere else would make the backend, and an agent's browser, fetch what a client chose.
 */
export const isMojangTexture = (texture: string): boolean =>
  Option.match(Option.flatMap(Result.getSuccess(decodeString(texture)), decodeTextures), {
    onNone: () => false,
    onSome: ({ textures }) => {
      const urls = Object.values(textures).map(({ url }) => URL.parse(url));
      return (
        urls.length > 0 &&
        urls.every(
          (url) =>
            url !== null &&
            (url.protocol === "http:" || url.protocol === "https:") &&
            url.hostname === "textures.minecraft.net",
        )
      );
    },
  });

const observe = (sighting: ItemSighting): Observation | null => {
  const texture = sighting.skullTexture ?? null;
  if (texture !== null && !isMojangTexture(texture)) {
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
    maxSamples: MAX_SAMPLES,
  });
});

export const appearances = Effect.flatMap(ItemStore, (store) => store.appearances);
