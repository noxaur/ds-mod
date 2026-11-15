import type { Line, Span } from "../items/model.ts";
import type { Catalog } from "../items/render/catalog.ts";
import { named, statValues } from "../items/render/facts.ts";
import { safeTexture } from "../items/render/policy.ts";
import { count, flag, record, text } from "../items/render/read.ts";
import { RARITY_COLORS } from "../items/render/tooltip.ts";
import { titleCase } from "../items/title.ts";
import type { Pet } from "./model.ts";

const GRAY = "#AAAAAA";
const WHITE = "#FFFFFF";

const label = (name: string, value: string, color = WHITE): Line => [
  { text: `${name}: `, color: GRAY },
  { text: value, color },
];

/** `1,234,567` for a number of whole units. */
const grouped = (value: number): string => Math.floor(value).toLocaleString("en-US");

/** Whether a skin is safe to hand a client, once for each texture of a catalog, because checking decodes it. */
const checked = new WeakMap<Catalog, Map<string, string | undefined>>();
const textureOf = (catalog: Catalog, value: string | undefined): string | undefined => {
  if (value === undefined) {
    return undefined;
  }
  const known = checked.get(catalog) ?? new Map<string, string | undefined>();
  checked.set(catalog, known);
  if (!known.has(value)) {
    known.set(value, safeTexture(value));
  }
  return known.get(value);
};

/**
 * The pet read from what the server sends, with what the catalog adds. `item` is the backend's own drawing of the
 * pet for a grid, because the server's API sends no tooltip for a pet: its head, its name in the rarity's color,
 * and a lore of the little that is known. The server's own tooltip would show the level and the abilities, and this has neither, because no pet XP table is known.
 */
export const petOf = (catalog: Catalog, raw: unknown): Pet => {
  const source = record(raw);
  const type = text(source["type"]) ?? "UNKNOWN";
  const species = catalog.pets.get(type.toLowerCase());
  const name = species?.display ?? titleCase(type);
  const rarity = text(source["rarity"]) ?? "COMMON";
  const held = text(source["equippedItem"]);
  const skin = text(source["skinOverride"]);
  const heldItem = held === undefined || held === "" ? null : named(catalog, held);
  const skinOverride = skin === undefined || skin === "" ? null : named(catalog, skin);
  const totalXp = count(source["totalXp"]) ?? 0;
  const candiesUsed = Math.floor(count(source["candiesUsed"]) ?? 0);
  const active = flag(source["active"]) ?? false;
  const skinTexture = skin === undefined ? undefined : catalog.items.get(skin)?.skin;
  const texture = textureOf(catalog, skinTexture) ?? textureOf(catalog, species?.skin);
  const color = RARITY_COLORS[rarity.toUpperCase()] ?? WHITE;
  const heldTier = held === undefined ? undefined : catalog.items.get(held)?.tier;
  const lore: Line[] = [
    ...(species?.category === undefined ? [] : [[{ text: `${titleCase(species.category)} Pet`, color: GRAY }]]),
    [],
    label("Total XP", grouped(totalXp)),
    ...(heldItem === null
      ? []
      : [label("Held Item", heldItem.name, RARITY_COLORS[heldTier?.toUpperCase() ?? ""] ?? WHITE)]),
    ...(skinOverride === null ? [] : [label("Skin", skinOverride.name)]),
    ...(candiesUsed > 0 ? [label("Candies Used", String(candiesUsed))] : []),
    ...(active ? [[{ text: "Active", color: "#55FF55" } satisfies Span]] : []),
    [],
    [{ text: rarity.replaceAll("_", " ").toUpperCase(), color, bold: true }],
  ];
  return {
    type,
    name,
    rarity,
    totalXp,
    xpCurve: count(source["xpCurve"]) ?? 0,
    candiesUsed,
    active,
    heldItem,
    skinOverride,
    level: null,
    maxLevel: species?.maxLevel === undefined ? null : Math.trunc(species.maxLevel),
    category: species?.category === undefined ? null : titleCase(species.category),
    perks: statValues(catalog, species?.perks ?? {}),
    description: species?.description ?? [],
    item: {
      entry: null,
      material: "minecraft:player_head",
      count: 1,
      name: [{ text: name, color }],
      lore,
      rarity,
      glint: false,
      ...(texture === undefined ? {} : { skullTexture: texture }),
    },
  };
};
