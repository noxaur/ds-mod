import { Schema } from "effect";

import { count, flag, lenient, record, type StatSet, statSet, strings, text } from "../../upstream/read.ts";

/** An item as dragsim.co's player API sends it, with every field that is missing or of the wrong type left out. */
export interface UpstreamItem {
  readonly entry: string | undefined;
  readonly count: number;
  readonly material: string;
  readonly name: string | undefined;
  readonly type: string;
  readonly rarity: string | undefined;
  readonly reforge: string;
  readonly color: unknown;
  /** `item_data.skull`, when the server says. */
  readonly skull: boolean | undefined;
  readonly reforgeStats: StatSet;
  readonly stats: StatSet;
  readonly books: number;
  readonly hotPotatoBooks: number;
  readonly fumingBooks: number;
  readonly finalSynchronizers: number;
  readonly npcSellPrice: number | undefined;
  /** `extra`, whose keys are the item's own modifiers, such as `drill`. */
  readonly extra: Readonly<Record<string, unknown>>;
  readonly artOfWar: boolean;
  readonly recombobulated: boolean;
  readonly stars: number;
  readonly rune: string | undefined;
  readonly enrichment: string | undefined;
  readonly kills: number;
  readonly skinId: string | undefined;
  readonly dye: unknown;
  /** The base64 skull texture the item carries. */
  readonly skin: unknown;
  readonly abilities: readonly string[];
}

const Stars = lenient(Schema.Struct({ stars: Schema.Finite }));
const SkinId = lenient(Schema.Struct({ skinId: Schema.String }));

/** The item a slot holds, or undefined for a slot that is empty or holds something that is not an item. */
export const readItem = (raw: unknown): UpstreamItem | undefined => {
  const item = record(raw);
  const material = text(item["material"]);
  if (material === undefined || material === "" || material === "minecraft:air") {
    return undefined;
  }
  const data = record(item["item_data"]);
  const modifiers = record(item["item_modifiers"]);
  const extra = record(item["extra"]);
  return {
    entry: text(item["entry"]) === "" ? undefined : text(item["entry"]),
    count: count(item["count"]) ?? 1,
    material,
    name: text(data["item_name"]),
    type: text(data["item_type"]) ?? "NONE",
    rarity: text(data["rarity"]),
    reforge: text(data["reforge"]) ?? "",
    color: data["color"],
    skull: flag(data["skull"]),
    reforgeStats: statSet(data["reforge_stats"]),
    stats: statSet(item["item_stats"]),
    books: (count(modifiers["hot_potato_books"]) ?? 0) + (count(modifiers["golden_hot_potato_books"]) ?? 0),
    hotPotatoBooks: count(modifiers["hot_potato_books"]) ?? 0,
    fumingBooks: count(modifiers["golden_hot_potato_books"]) ?? 0,
    finalSynchronizers: count(modifiers["final_synchronizers"]) ?? 0,
    npcSellPrice: count(data["npc_sell_price"]),
    extra,
    artOfWar: flag(modifiers["art_of_war"]) ?? false,
    recombobulated: flag(modifiers["recombobulated"]) ?? false,
    stars: Math.max(count(modifiers["star_level"]) ?? 0, Stars(extra["dungeon_upgrade"])?.stars ?? 0),
    rune: text(modifiers["rune_id"]),
    enrichment: text(modifiers["enrichment"]),
    kills: count(modifiers["kills"]) ?? 0,
    skinId: SkinId(extra["item_skin"])?.skinId,
    dye: extra["item_dye"],
    skin: record(item["item_skin"])["value"],
    abilities: strings(item["abilities"]) ?? [],
  };
};
