import { Schema } from "effect";
import { expect, test } from "vite-plus/test";

import games from "../fixtures/game-items.json" with { type: "json" };
import { toUpstream } from "./fromGame.testing.ts";
import { gameData } from "./gameData.ts";

const leaves = (value: unknown, path = ""): Record<string, unknown> => {
  if (typeof value === "boolean") {
    return { [path]: value ? 1 : 0 };
  }
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return { [path]: value };
  }
  return Object.fromEntries(
    Object.entries(value).flatMap(([key, inner]) =>
      Object.entries(leaves(inner, path === "" ? key : `${path}.${key}`)),
    ),
  );
};

const Line = Schema.Struct({ spans: Schema.Array(Schema.Record(Schema.String, Schema.Unknown)) });
const real = Schema.decodeUnknownSync(
  Schema.Array(
    Schema.Struct({
      id: Schema.String,
      count: Schema.Number,
      name: Line,
      lore: Schema.Array(Line),
      data: Schema.Record(Schema.String, Schema.Unknown),
    }),
  ),
)(games);

test("an item's data is rebuilt as the game holds it, from what the player API sends for it", () => {
  for (const game of real) {
    const ours = leaves(gameData(toUpstream(game)));
    const theirs = leaves(game.data);
    for (const [path, value] of Object.entries(ours)) {
      expect.soft(theirs[path], `${String(theirs["item_data.item_id"])} ${path}`).toStrictEqual(value);
    }
  }
});

test("the keys a mod reads first are where the game has them", () => {
  const data = gameData({
    entry: "giants_sword",
    count: 1,
    material: "minecraft:golden_sword",
    item_data: { rarity: "MYTHIC", item_type: "SWORD", reforge_stats: { base: { damage: 30 } } },
    item_stats: { base: { damage: 730 } },
    item_modifiers: { recombobulated: true },
    abilities: ["giants_slam"],
  });
  expect(data).toStrictEqual({
    item_data: {
      rarity: "MYTHIC",
      item_type: "SWORD",
      item_id: "giants_sword",
      reforge_stats: { stats: { damage: 30 } },
    },
    item_stats: { stats: { damage: 730 } },
    item_modifiers: { recombobulated: true },
    abilities: { abilities: ["giants_slam"] },
  });
});
