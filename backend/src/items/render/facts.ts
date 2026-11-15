import { Schema } from "effect";

import { colorOf } from "../legacy.ts";
import type { ItemFacts, Named, StatSheet, StatValue } from "../model.ts";
import { titleCase } from "../title.ts";
import type { Catalog } from "./catalog.ts";
import { count, flag, lenient, numbers, record, text } from "./read.ts";
import type { StatSet, UpstreamItem } from "./upstream.ts";

const GRAY = "#AAAAAA";

/** An id and the catalog item's name for it, or the id in title case. */
export const named = (catalog: Catalog, id: string): Named => ({
  id,
  name: catalog.items.get(id)?.name ?? titleCase(id),
});

export const statValues = (catalog: Catalog, stats: Readonly<Record<string, number>>): readonly StatValue[] => {
  const { statOrder } = catalog.rules;
  const order = (id: string): number => {
    const at = statOrder.indexOf(id);
    return at === -1 ? statOrder.length : at;
  };
  return Object.entries(stats)
    .toSorted(([a], [b]) => order(a) - order(b))
    .map(([id, value]) => {
      const info = catalog.stats.get(id);
      return {
        id,
        name: info?.name ?? titleCase(id),
        color: colorOf(info?.color ?? "") ?? GRAY,
        value,
      };
    });
};

/** The four groups of a player's or an item's stats, each named and colored from the catalog. */
export const statSheet = (catalog: Catalog, set: StatSet): StatSheet => ({
  base: statValues(catalog, set.base),
  additive: statValues(catalog, set.additive),
  multiplicative: statValues(catalog, set.multiplicative),
  increaseMax: statValues(catalog, set.increaseMax),
});

const empty = (set: StatSet): boolean =>
  [set.base, set.additive, set.multiplicative, set.increaseMax].every((group) => Object.keys(group).length === 0);

const Scalars = lenient(Schema.Record(Schema.String, Schema.Union([Schema.String, Schema.Finite, Schema.Boolean])));
const Part = lenient(Schema.String);

/** A part's id, such as the drill's engine, with its name. */
const part = (catalog: Catalog, raw: unknown): Named | undefined => {
  const id = Part(raw);
  return id === undefined || id === "" ? undefined : named(catalog, id);
};

const readDrill = (catalog: Catalog, raw: unknown): NonNullable<ItemFacts["drill"]> | undefined => {
  const drill = record(raw);
  const [fuel, engine, tank, module, coated] = [
    count(drill["fuel"]),
    part(catalog, drill["engine"]),
    part(catalog, drill["tank"]),
    part(catalog, drill["module"]),
    flag(drill["coated"]),
  ];
  const found = {
    ...(fuel === undefined ? {} : { fuel }),
    ...(engine === undefined ? {} : { engine }),
    ...(tank === undefined ? {} : { tank }),
    ...(module === undefined ? {} : { module }),
    ...(coated === undefined ? {} : { coated }),
  };
  return Object.keys(found).length === 0 ? undefined : found;
};

/**
 * What the item's data says, as the tooltip's numbers come from it. Everything is read from what `readItem` and the
 * catalog already have, and a key is left out when the item has none of it.
 */
export const factsOf = (catalog: Catalog, item: UpstreamItem): ItemFacts | undefined => {
  const abilities = item.abilities.map((key): Named => {
    const ability =
      (item.entry === undefined ? undefined : catalog.itemAbilities.get(item.entry)?.get(key)) ??
      catalog.abilities.get(key);
    return { id: key, name: ability?.name ?? titleCase(key) };
  });
  const dye = text(record(item.dye)["dyeId"]);
  const drill = readDrill(catalog, item.extra["drill"]);
  const coating = Scalars(item.extra["coating"]) ?? {};
  const attunement = Scalars(item.extra["attunement"]) ?? {};
  const scrolls = Object.entries(numbers(item.extra["wither_scrolls"]) ?? {}).map(([id, value]) => ({
    ...named(catalog, id),
    value,
  }));
  const upgrade = numbers(item.extra["dungeon_upgrade"]) ?? {};
  const facts: ItemFacts = {
    ...(item.type === "NONE" || item.type === "" ? {} : { itemType: item.type }),
    ...(item.npcSellPrice === undefined ? {} : { npcSellPrice: item.npcSellPrice }),
    ...(item.reforge === "" ? {} : { reforge: item.reforge }),
    ...(item.stars > 0 ? { stars: Math.trunc(item.stars) } : {}),
    ...(item.hotPotatoBooks > 0 ? { hotPotatoBooks: Math.trunc(item.hotPotatoBooks) } : {}),
    ...(item.fumingBooks > 0 ? { fumingBooks: Math.trunc(item.fumingBooks) } : {}),
    ...(item.recombobulated ? { recombobulated: true } : {}),
    ...(item.artOfWar ? { artOfWar: true } : {}),
    ...(item.finalSynchronizers > 0 ? { finalSynchronizers: Math.trunc(item.finalSynchronizers) } : {}),
    ...(item.rune === undefined ? {} : { rune: named(catalog, item.rune) }),
    ...(item.enrichment === undefined ? {} : { enrichment: named(catalog, item.enrichment) }),
    ...(item.kills > 0 ? { kills: item.kills } : {}),
    ...(empty(item.stats) ? {} : { stats: statSheet(catalog, item.stats) }),
    ...(empty(item.reforgeStats) ? {} : { reforgeStats: statSheet(catalog, item.reforgeStats) }),
    ...(abilities.length > 0 ? { abilities } : {}),
    ...(item.skinId === undefined ? {} : { skin: named(catalog, item.skinId) }),
    ...(dye === undefined ? {} : { dye: named(catalog, dye) }),
    ...(drill === undefined ? {} : { drill }),
    ...(Object.keys(coating).length > 0 ? { coating } : {}),
    ...(Object.keys(attunement).length > 0 ? { attunement } : {}),
    ...(scrolls.length > 0 ? { witherScrolls: scrolls } : {}),
    ...(Object.keys(upgrade).length > 0 ? { dungeonUpgrade: upgrade } : {}),
  };
  return Object.keys(facts).length === 0 ? undefined : facts;
};
