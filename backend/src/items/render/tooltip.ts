import { colorOf, parseLegacy, push } from "../legacy.ts";
import type { Line, Span } from "../model.ts";
import { titleCase } from "../title.ts";
import type { Ability, Catalog, CatalogItem } from "./catalog.ts";
import { advance } from "./font.ts";
import type { UpstreamItem } from "./upstream.ts";

const WHITE = "#FFFFFF";
const GRAY = "#AAAAAA";
const DARK_GRAY = "#555555";
const BLUE = "#5555FF";
const YELLOW = "#FFFF55";
const GOLD = "#FFAA00";
const GREEN = "#55FF55";
const AQUA = "#00AAAA";
const PINK = "#FF55FF";

export const RARITY_COLORS: Readonly<Record<string, string>> = {
  COMMON: "#FFFFFF",
  UNCOMMON: "#55FF55",
  RARE: "#5555FF",
  EPIC: "#AA00AA",
  LEGENDARY: "#FFAA00",
  MYTHIC: "#FF55FF",
  DIVINE: "#55FFFF",
  SPECIAL: "#FF5555",
  VERY_SPECIAL: "#FF5555",
  SUPREME: "#AA0000",
};

/** The icon the server draws before a stat, which the catalog does not carry. The ones past `speed` are guesses. */
const ICONS: Readonly<Record<string, string>> = {
  damage: "\ue000",
  strength: "❁",
  critical_chance: "☣",
  critical_damage: "\ue001",
  health: "\ue003",
  defense: "\ue004",
  speed: "\ue006",
  intelligence: "\ue007",
  ability_damage: "๑",
  mining_speed: "⸕",
  breaking_power: "⸕",
  attack_speed: "⚔",
  ferocity: "⫽",
  magic_find: "✯",
  true_defense: "❂",
  pet_luck: "♣",
  mining_fortune: "☘",
  mining_wisdom: "☯",
  wisdom: "☯",
  pristine: "✧",
  vitality: "♨",
};

/** The stats a dungeon scales. The rest, such as speed and crit chance, are drawn the same in a dungeon. */
const SCALED = new Set(["damage", "strength", "critical_damage", "health", "defense", "intelligence", "true_defense"]);
const PERCENT = new Set(["critical_chance", "critical_damage", "attack_speed", "ability_damage"]);
const ARMOR = new Set(["HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"]);
const WEAPON = new Set(["SWORD", "BOW", "WAND", "AXE", "HOE", "SPADE"]);

/** Whole numbers stay whole, and the rest keep up to two decimals, so `19.999999999999996` reads `20`. */
export const num = (value: number): string => String(Math.round(value * 100) / 100);

const grouped = (value: number): string => value.toLocaleString("en-US", { maximumFractionDigits: 2 });

const plain = (line: Line): string => line.map((span) => span.text).join("");

const bar = (width: number): string => `\ue100\ue200${"\ue101\ue200".repeat(width)}\ue102\ue200`;

/** The server's own marker before text of this width, which moves the cursor so the text sits inside its box. */
const gap = (base: number, width: number): string => String.fromCodePoint(base + Math.min(Math.max(width, 0), 255));

const styleOf = ({ text: _text, ...style }: Span): Omit<Span, "text"> => style;

const badge = (color: string, middle: readonly Span[]): Span[] => {
  const width = advance(middle.map((span) => span.text).join(""));
  const spans: Span[] = [];
  push(spans, bar(width + 4), { color });
  push(spans, gap(0xe200, width + 5), { color: WHITE });
  for (const span of middle) {
    push(spans, span.text, { color, ...styleOf(span) });
  }
  push(spans, "\ue405", { color: WHITE });
  return spans;
};

interface Stat {
  readonly id: string;
  readonly line: Line;
}

const statLine = (
  catalog: Catalog,
  item: UpstreamItem,
  id: string,
  dungeon: boolean,
  books: number,
): Line | undefined => {
  const { rules } = catalog;
  const kind = ARMOR.has(item.type) ? rules.armorBook : WEAPON.has(item.type) ? rules.weaponBook : {};
  const book = (kind[id] ?? 0) * books;
  const aow = item.artOfWar && id === "damage" ? rules.artOfWarDamage : 0;
  const enrich = item.enrichment !== undefined && id === rules.enrichmentStat ? rules.enrichmentAmount : 0;
  const reforge = (item.reforgeStats.base[id] ?? 0) + (item.reforgeStats.additive[id] ?? 0);
  const total =
    ((item.stats.base[id] ?? 0) + (item.stats.additive[id] ?? 0) + book + aow + enrich) *
    (1 + (item.stats.multiplicative[id] ?? 0));
  if (total === 0 && reforge === 0) {
    return undefined;
  }
  const info = catalog.stats.get(id);
  const color = colorOf(info?.color ?? "7") ?? GRAY;
  const suffix = PERCENT.has(id) ? "%" : "";
  const parts: { text: string; color: string }[] = [{ text: `+${num(total)}${suffix}`, color }];
  if (reforge !== 0) {
    parts.push({ text: `(+${num(reforge)}${suffix})`, color: BLUE });
  }
  if (book + aow !== 0) {
    parts.push({ text: `(+${num(book + aow)}${suffix})`, color: YELLOW });
  }
  if (enrich !== 0) {
    parts.push({ text: `(+${num(enrich)}${suffix})`, color: PINK });
  }
  if (dungeon) {
    const scale = SCALED.has(id) ? 1 + rules.dungeonBonus + item.stars * rules.starMultiplier : 1;
    parts.push({ text: `(+${num(total * scale)}${suffix})`, color: DARK_GRAY });
  }
  const line: Span[] = [];
  push(line, `${ICONS[id] ?? "✦"} `, { color });
  push(line, `${info?.name ?? titleCase(id)}: `, { color: GRAY });
  for (const part of parts) {
    push(line, part.color === DARK_GRAY ? part.text : `${part.text} `, { color: part.color });
  }
  return line;
};

const stats = (catalog: Catalog, item: UpstreamItem, dungeon: boolean): readonly Line[] => {
  const { rules } = catalog;
  const kind = ARMOR.has(item.type) ? rules.armorBook : WEAPON.has(item.type) ? rules.weaponBook : {};
  const ids = new Set([...Object.keys(item.stats.base), ...Object.keys(item.stats.additive)]);
  if (item.books > 0) {
    Object.keys(kind).forEach((id) => ids.add(id));
  }
  if (item.artOfWar) {
    ids.add("damage");
  }
  if (item.enrichment !== undefined && rules.enrichmentStat !== "") {
    ids.add(rules.enrichmentStat);
  }
  const order = (id: string): number => {
    const at = rules.statOrder.indexOf(id);
    return at === -1 ? rules.statOrder.length : at;
  };
  const lines: Stat[] = [];
  for (const id of [...ids].toSorted((a, b) => order(a) - order(b))) {
    const line = statLine(catalog, item, id, dungeon, item.books);
    if (line !== undefined) {
      lines.push({ id, line });
    }
  }
  return lines.map((stat) => stat.line);
};

const abilityBody = (ability: Ability): Line[] => {
  const lines: Line[] = [...ability.lines];
  if (ability.cost > 0) {
    lines.push([
      { text: "Mana Cost: ", color: DARK_GRAY },
      { text: num(ability.cost), color: AQUA },
    ]);
  }
  if (ability.cooldown > 0) {
    lines.push([
      { text: "Cooldown: ", color: DARK_GRAY },
      { text: `${num(Math.round(ability.cooldown * 10) / 10)}s`, color: GREEN },
    ]);
  }
  return lines;
};

/** The header above an ability's text, laid out to the width `wide` of the widest line of the tooltip. */
const abilityHeader = (ability: Ability, wide: number): Line => {
  const header: Span[] = [];
  const triggered = ability.trigger !== "" && ability.trigger !== "PASSIVE" && ability.trigger !== "FULL SET BONUS";
  if (ability.trigger === "FULL SET BONUS") {
    const set = ability.fullSet;
    push(header, `${set ? "Full Set Bonus" : "Piece Bonus"}: ${ability.name}${set ? " " : ""}`, { color: GOLD });
    if (set) {
      push(header, `(0/${String(ability.maxCount)})`, { color: GRAY });
    }
  } else {
    push(header, ability.name, { color: GOLD });
  }
  const used = advance(plain(header));
  if (triggered) {
    const space = wide - used - advance(ability.trigger) - 13;
    if (space >= 0) {
      push(header, gap(0xe400, space), { color: WHITE });
    }
    for (const span of badge(YELLOW, [{ text: ability.trigger }])) {
      push(header, span.text, styleOf(span));
    }
    return header;
  }
  const pad = wide - used - 9;
  if (pad >= 0) {
    push(header, "\ue407", { color: WHITE });
    push(header, "\ue103\ue200".repeat(Math.floor(pad / 8)), { color: DARK_GRAY });
    push(header, gap(0xe400, pad % 8), { color: WHITE });
  }
  return header;
};

const footer = (item: UpstreamItem, catalogItem: CatalogItem | undefined, dungeon: boolean): Line | undefined => {
  const rarity = (item.rarity ?? catalogItem?.tier)?.toUpperCase();
  const color = RARITY_COLORS[rarity ?? ""] ?? WHITE;
  const line: Span[] = [];
  if (rarity !== undefined) {
    const label = rarity.replaceAll("_", " ");
    const middle: Span[] = item.recombobulated
      ? [{ text: "0", obfuscated: true }, { text: ` ${label} ` }, { text: "0", obfuscated: true }]
      : [{ text: label }];
    for (const span of badge(color, middle)) {
      push(line, span.text, styleOf(span));
    }
  }
  if (item.type !== "NONE" && item.type !== "") {
    const kind = `${dungeon ? "DUNGEON " : ""}${item.type.replaceAll("_", " ")}`;
    if (line.length > 0) {
      push(line, "\ue405", { color: WHITE });
    }
    for (const span of badge(GRAY, [{ text: kind }])) {
      push(line, span.text, styleOf(span));
    }
  }
  return line.length === 0 ? undefined : line;
};

const STAR = "✪";
const MASTER_STARS = "➊➋➌➍➎";

/** The name as the game shows it: the reforge in front, the item in its rarity's color, and the dungeon stars after. */
export const name = (item: UpstreamItem, catalogItem: CatalogItem | undefined, fallback: string): Line => {
  const rarity = (item.rarity ?? catalogItem?.tier)?.toUpperCase() ?? "COMMON";
  const color = RARITY_COLORS[rarity] ?? WHITE;
  const base = item.name ?? catalogItem?.name ?? fallback;
  const spans = [...parseLegacy(`${item.reforge === "" ? "" : `${item.reforge} `}${base}`, color)];
  if (item.stars > 0) {
    push(spans, " ", { color });
    push(spans, STAR.repeat(Math.min(item.stars, 5)), { color: GOLD });
    if (item.stars > 5) {
      push(spans, MASTER_STARS.charAt(Math.min(item.stars, 10) - 6), { color: "#FF5555" });
    }
  }
  return spans;
};

/**
 * Most plain items, the ones with no type and no ability, have a recipe, which the server says in a line of its own.
 * Nothing the API sends says which do, so this is a guess that is right for most of them.
 */
const RECIPES: Line = [{ text: "Right-click to view recipes!", color: YELLOW }];

const rune = (id: string): Line => [{ text: `◆ ${titleCase(id)}`, color: PINK }];

const kills = (count: number): Line => [
  { text: "Kills: ", color: GRAY },
  { text: grouped(count), color: "#FF5555" },
];

const blocks = (sections: readonly (readonly Line[])[]): Line[] => {
  const lines: Line[] = [];
  for (const section of sections.filter((s) => s.length > 0)) {
    if (lines.length > 0) {
      lines.push([]);
    }
    lines.push(...section);
  }
  return lines;
};

/** The lines under the name: stats, what the reforge adds, the description, the abilities, and the rarity footer. */
export const lore = (
  catalog: Catalog,
  item: UpstreamItem,
  catalogItem: CatalogItem | undefined,
): { readonly lines: readonly Line[]; readonly dungeon: boolean } => {
  const dungeon = item.entry !== undefined && catalog.dungeon.has(item.entry);
  const own = item.entry === undefined ? undefined : catalog.itemAbilities.get(item.entry);
  const abilities = item.abilities
    .map((key) => own?.get(key) ?? catalog.abilities.get(key))
    .filter((ability): ability is Ability => ability !== undefined && !ability.hidden);
  const statLines = stats(catalog, item, dungeon);
  const reforge = catalog.reforges.get(item.reforge.toLowerCase()) ?? [];
  const extras: Line[] = [];
  if (item.rune !== undefined) {
    extras.push(rune(item.rune));
  }
  if (item.kills > 0) {
    extras.push(kills(item.kills));
  }
  const description = catalogItem?.description ?? [];
  const wide = Math.max(
    0,
    ...[...statLines, ...reforge, ...extras, ...description, ...abilities.flatMap(abilityBody)].map((line) =>
      advance(plain(line)),
    ),
  );
  const body = blocks([
    statLines,
    reforge,
    extras,
    description,
    ...abilities.map((ability) => [abilityHeader(ability, wide), ...abilityBody(ability)]),
  ]);
  const end = footer(item, catalogItem, dungeon);
  const recipes = item.type === "NONE" && item.abilities.length === 0 ? [RECIPES] : [];
  const lines: Line[] = [...body];
  if (end !== undefined) {
    if (body.length > 0 && (recipes.length > 0 || !dungeon)) {
      lines.push([]);
    }
    lines.push(...recipes);
    if (body.length > 0 || recipes.length > 0) {
      lines.push([]);
    }
    lines.push(end);
  }
  return { lines, dungeon };
};
