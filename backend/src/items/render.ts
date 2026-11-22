import { Effect } from "effect";

import type { DrawnItem } from "./model.ts";
import { decodeCatalog } from "./render/catalog.ts";
import { factsOf } from "./render/facts.ts";
import { gameData } from "./render/gameData.ts";
import { glints, skullTexture } from "./render/policy.ts";
import { parseColor, record, text } from "../upstream/read.ts";
import { lore, name } from "./render/tooltip.ts";
import { readItem } from "./render/upstream.ts";
import { type ItemHints, ItemStore } from "./store.ts";

export const entriesOf = (items: readonly unknown[]): readonly string[] => {
  const entries = new Set<string>();
  for (const item of items) {
    const entry = text(record(item)["entry"]);
    if (entry !== undefined && entry !== "") {
      entries.add(entry);
    }
  }
  return [...entries];
};

export interface Renderer {
  readonly render: (item: unknown) => DrawnItem | null;
}

const MAX_COUNT = 99;

const wornAbilities = (worn: readonly unknown[]): ReadonlyMap<string, number> => {
  const abilities = new Map<string, number>();
  for (const raw of worn) {
    for (const key of new Set(readItem(raw)?.abilities ?? [])) {
      abilities.set(key, (abilities.get(key) ?? 0) + 1);
    }
  }
  return abilities;
};

export const makeRenderer = (
  catalogJson: unknown,
  hints: ReadonlyMap<string, ItemHints>,
  worn: readonly unknown[] = [],
): Renderer => {
  const catalog = decodeCatalog(catalogJson);
  const equipped = wornAbilities(worn);
  return {
    render: (raw) => {
      const item = readItem(raw);
      if (item === undefined || item.count < 1) {
        return null;
      }
      const entry = item.entry === undefined ? undefined : catalog.items.get(item.entry);
      const hint = item.entry === undefined ? undefined : hints.get(item.entry);
      const texture =
        item.material === "minecraft:player_head" ? skullTexture(item.skin, item.skinId, hint, entry?.skin) : undefined;
      const color = parseColor(item.color) ?? parseColor(record(item.dye)["color"] ?? item.dye) ?? entry?.color;
      const { lines } = lore(catalog, item, entry, equipped);
      const facts = factsOf(catalog, item);
      return {
        entry: item.entry ?? null,
        material: item.material,
        count: Math.min(Math.max(Math.trunc(item.count), 1), MAX_COUNT),
        name: name(item, entry, item.entry ?? item.material),
        lore: lines,
        rarity: item.rarity ?? entry?.tier ?? null,
        glint: glints(item.material, entry?.glowing ?? false, hint?.glint),
        ...(texture === undefined ? {} : { skullTexture: texture }),
        ...(color === undefined ? {} : { color }),
        ...(facts === undefined ? {} : { facts }),
        customData: gameData(raw),
      };
    },
  };
};

export const rendererFor = Effect.fn(function* (
  catalogJson: unknown,
  items: readonly unknown[],
  worn: readonly unknown[] = [],
) {
  const hints = yield* (yield* ItemStore).hints(entriesOf(items));
  return makeRenderer(catalogJson, hints, worn);
});

export type { Catalog } from "./render/catalog.ts";
export { named, statSheet, statValues } from "./render/facts.ts";
export { decodeCatalog };
