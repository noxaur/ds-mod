import type { Line, Span } from "../items/model.ts";
import { type Catalog, named, statValues } from "../items/render.ts";
import { RARITY_COLORS } from "../upstream/rarity.ts";
import { count, flag, record, text } from "../upstream/read.ts";
import { safeTexture } from "../upstream/texture.ts";
import { titleCase } from "../upstream/title.ts";
import type { Pet } from "./model.ts";

const GRAY = "#AAAAAA";
const WHITE = "#FFFFFF";

const label = (name: string, value: string, color = WHITE): Line => [
  { text: `${name}: `, color: GRAY },
  { text: value, color },
];

/** `1,234,567` for a number of whole units. */
const grouped = (value: number): string => Math.floor(value).toLocaleString("en-US");

const ORDINARY_CURVE: Readonly<Record<string, readonly [number, number]>> = {
  COMMON: [100, 75],
  UNCOMMON: [175, 125],
  RARE: [275, 175],
  EPIC: [400, 250],
  LEGENDARY: [600, 350],
  MYTHIC: [800, 500],
};

const petLevel = (species: ReturnType<Catalog["pets"]["get"]>, type: string, rarity: string, totalXp: number) => {
  const golden = type.toLowerCase() === "golden_dragon";
  const maxLevel = golden ? Math.trunc(species?.maxLevel ?? 200) : 100;
  if (!golden && species?.levels !== true) {
    return { level: maxLevel, maxLevel, xpIntoLevel: 0, xpForNextLevel: null };
  }
  const [base, growth] = ORDINARY_CURVE[rarity.toUpperCase()] ?? [100, 75];
  const step = (level: number): number =>
    golden ? (level === 101 ? 650 : 204_075) : Math.floor((base + growth * level) * (1 + 3 * (level / 100) ** 2));
  let level = golden ? 101 : 1;
  let xpIntoLevel = totalXp;
  while (level < maxLevel && xpIntoLevel >= step(level)) {
    xpIntoLevel -= step(level);
    level += 1;
  }
  return { level, maxLevel, xpIntoLevel, xpForNextLevel: level < maxLevel ? step(level) : null };
};

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
  const progress = petLevel(species, type, rarity, totalXp);
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
    ...progress,
    category: species?.category === undefined ? null : titleCase(species.category),
    perks: statValues(catalog, species?.perks ?? {}),
    description: species?.description ?? [],
    item: {
      entry: null,
      material: "minecraft:player_head",
      count: 1,
      name: [
        { text: `[Lvl ${progress.level}] `, color: GRAY },
        { text: name, color },
      ],
      lore,
      rarity,
      glint: false,
      ...(texture === undefined ? {} : { skullTexture: texture }),
    },
  };
};
