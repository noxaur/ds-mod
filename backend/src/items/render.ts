import type { DrawnItem } from "./model.ts";
import { decodeCatalog } from "./render/catalog.ts";
import { factsOf } from "./render/facts.ts";
import { gameData } from "./render/gameData.ts";
import { glints, skullTexture } from "./render/policy.ts";
import { parseColor, record, text } from "./render/read.ts";
import { lore, name } from "./render/tooltip.ts";
import { readItem } from "./render/upstream.ts";
import type { ItemHints } from "./store.ts";

/** The entries upstream slots hold, as dragsim.co's player API sends them, to ask the store for hints about. */
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

/** Draws dragsim.co's items as the game does. */
export interface Renderer {
  /** The item one upstream slot holds, as dragsim.co's player API sends it, or null when it holds none. */
  readonly render: (item: unknown) => DrawnItem | null;
}

const MAX_COUNT = 99;

/** A renderer for dragsim.co's `catalog.json` and the hints players agree on. Make one per request. */
export const makeRenderer = (catalogJson: unknown, hints: ReadonlyMap<string, ItemHints>): Renderer => {
  const catalog = decodeCatalog(catalogJson);
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
      const { lines } = lore(catalog, item, entry);
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

export { decodeCatalog };
