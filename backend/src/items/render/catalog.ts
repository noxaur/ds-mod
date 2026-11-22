import { Schema } from "effect";

import { parseLegacy } from "../../upstream/legacy.ts";
import type { Line } from "../model.ts";
import { count, flag, lenient, lists, numbers, parseColor, record, strings, text } from "../../upstream/read.ts";

const skin = lenient(Schema.Struct({ value: Schema.String }));

export interface CatalogItem {
  readonly name: string | undefined;
  readonly tier: string | undefined;
  readonly glowing: boolean;
  readonly skin: string | undefined;
  readonly color: number | undefined;
  readonly description: readonly Line[];
}

export interface Ability {
  readonly key: string;
  readonly name: string;
  readonly trigger: string;
  readonly hidden: boolean;
  readonly fullSet: boolean;
  readonly maxCount: number;
  readonly lines: readonly Line[];
  readonly cost: number;
  readonly cooldown: number;
}

export interface CatalogPet {
  readonly display: string;
  readonly maxLevel: number | undefined;
  readonly levels: boolean;
  readonly category: string | undefined;
  readonly skin: string | undefined;
  readonly perks: Readonly<Record<string, number>>;
  readonly description: readonly Line[];
}

export interface Rules {
  readonly weaponBook: Readonly<Record<string, number>>;
  readonly armorBook: Readonly<Record<string, number>>;
  readonly starMultiplier: number;
  readonly artOfWarDamage: number;
  readonly enrichmentAmount: number;
  readonly enrichmentStat: string;
  // @keep What a dungeon item adds at Catacombs level 0, as a fraction.
  readonly dungeonBonus: number;
  readonly statOrder: readonly string[];
}

export interface Stat {
  // @keep The `§` code of the stat's color, such as `c`.
  readonly color: string;
  readonly name: string;
}

export interface Catalog {
  readonly items: ReadonlyMap<string, CatalogItem>;
  readonly dungeon: ReadonlySet<string>;
  // @keep Abilities by item in the catalog's order, and by key for the ones an item has but its entry does not list.
  readonly itemAbilities: ReadonlyMap<string, ReadonlyMap<string, Ability>>;
  readonly abilities: ReadonlyMap<string, Ability>;
  readonly reforges: ReadonlyMap<string, readonly Line[]>;
  readonly stats: ReadonlyMap<string, Stat>;
  readonly pets: ReadonlyMap<string, CatalogPet>;
  readonly rules: Rules;
}

const readItem = (raw: unknown): CatalogItem => {
  const item = record(raw);
  return {
    name: text(item["name"]),
    tier: text(item["tier"]),
    glowing: flag(item["glowing"]) ?? false,
    skin: skin(item["skin"])?.value,
    color: parseColor(item["color"]),
    description: (strings(item["description"]) ?? []).map((line) => parseLegacy(line)),
  };
};

const readPet = (raw: unknown): CatalogPet | undefined => {
  const pet = record(raw);
  const display = text(pet["display"]);
  if (display === undefined) {
    return undefined;
  }
  const perks: Record<string, number> = {};
  for (const perk of lists(pet["perks"]) ?? []) {
    const { stat, per_level } = record(perk);
    if (typeof stat === "string" && typeof per_level === "number") {
      perks[stat] = per_level;
    }
  }
  return {
    display,
    maxLevel: count(pet["max_level"]),
    levels: flag(pet["levels"]) ?? false,
    category: text(pet["category"]),
    skin: skin(pet["skin"])?.value,
    perks,
    description: (strings(pet["description"]) ?? []).map((line) => parseLegacy(line)),
  };
};

const readAbility = (raw: unknown): Ability | undefined => {
  const ability = record(raw);
  const key = text(ability["key"]);
  if (key === undefined) {
    return undefined;
  }
  return {
    key,
    name: text(ability["name"]) ?? key,
    trigger: text(ability["trigger"]) ?? "",
    hidden: flag(ability["hidden"]) ?? false,
    fullSet: flag(ability["fullSet"]) ?? false,
    maxCount: count(ability["maxCount"]) ?? 0,
    lines: (strings(ability["lines"]) ?? []).map((line) => parseLegacy(line, "#AAAAAA")),
    cost: count(ability["cost"]) ?? 0,
    cooldown: count(ability["cooldown"]) ?? 0,
  };
};

const readRules = (raw: unknown): Rules => {
  const rules = record(raw);
  const bonuses = lenient(Schema.Array(Schema.Finite))(rules["catacombsBonuses"]);
  return {
    weaponBook: numbers(rules["weaponBook"]) ?? {},
    armorBook: numbers(rules["armorBook"]) ?? {},
    starMultiplier: count(rules["starMultiplier"]) ?? 0,
    artOfWarDamage: count(rules["artOfWarDamage"]) ?? 0,
    enrichmentAmount: count(rules["enrichmentAmount"]) ?? 0,
    enrichmentStat: text(rules["enrichmentStat"]) ?? "",
    dungeonBonus: bonuses?.[0] ?? 0,
    statOrder: strings(rules["statOrder"]) ?? [],
  };
};

const decode = (source: unknown): Catalog => {
  const catalog = record(source);
  const items = new Map<string, CatalogItem>();
  for (const [id, item] of Object.entries(record(catalog["items"]))) {
    items.set(id, readItem(item));
  }
  const dungeon = new Set<string>();
  const itemAbilities = new Map<string, ReadonlyMap<string, Ability>>();
  const abilities = new Map<string, Ability>();
  for (const [id, lore] of Object.entries(record(catalog["itemLore"]))) {
    const entry = record(lore);
    if (flag(entry["nativeDungeon"]) === true) {
      dungeon.add(id);
    }
    const own = new Map<string, Ability>();
    for (const raw of lists(entry["abilities"]) ?? []) {
      const ability = readAbility(raw);
      if (ability !== undefined) {
        own.set(ability.key, ability);
        if (!abilities.has(ability.key)) {
          abilities.set(ability.key, ability);
        }
      }
    }
    itemAbilities.set(id, own);
  }
  const reforges = new Map<string, readonly Line[]>();
  for (const [name, lines] of Object.entries(record(catalog["reforges"]))) {
    reforges.set(
      name,
      (strings(lines) ?? []).map((line) => parseLegacy(line, "#AAAAAA")),
    );
  }
  const stats = new Map<string, Stat>();
  for (const raw of lists(catalog["stats"]) ?? []) {
    const stat = record(raw);
    const id = text(stat["id"]);
    if (id !== undefined) {
      stats.set(id, { color: text(stat["color"]) ?? "7", name: text(stat["name"]) ?? id });
    }
  }
  const pets = new Map<string, CatalogPet>();
  for (const [id, raw] of Object.entries(record(catalog["pets"]))) {
    const pet = readPet(raw);
    if (pet !== undefined) {
      pets.set(id, pet);
    }
  }
  return { items, dungeon, itemAbilities, abilities, reforges, stats, pets, rules: readRules(catalog["itemRules"]) };
};

const decoded = new WeakMap<object, Catalog>();

/** @keep A request passes the same parsed catalog each time and decoding it is slow, so the result is kept. */
export const decodeCatalog = (raw: unknown): Catalog => {
  if (typeof raw !== "object" || raw === null) {
    return decode(raw);
  }
  const known = decoded.get(raw);
  if (known !== undefined) {
    return known;
  }
  const fresh = decode(raw);
  decoded.set(raw, fresh);
  return fresh;
};
