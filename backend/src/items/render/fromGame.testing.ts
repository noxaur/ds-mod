import { numberEntries, record } from "./read.ts";

/**
 * An item as the mod sees it in the game: its `id`, its tooltip and the server's `custom_data`. The tests turn one into
 * what dragsim.co's player API would have sent for it, to render it and compare the result with the tooltip the game
 * drew.
 */
export interface GameItem {
  readonly id: string;
  readonly count: number;
  readonly name: { readonly spans: readonly object[] };
  readonly lore: readonly { readonly spans: readonly object[] }[];
  readonly data: Readonly<Record<string, unknown>>;
}

/** The game's stat set, whose base stats are `stats`, in the API's shape, whose base stats are `base`. */
const statSet = (value: unknown) => {
  const set = record(value);
  return {
    base: numberEntries(set["stats"]),
    additive: numberEntries(set["additive"]),
    multiplicative: numberEntries(set["multiplicative"]),
    increase_max: numberEntries(set["increase_max"]),
  };
};

/** What the player API sends for a game item. */
export const toUpstream = (game: GameItem): Record<string, unknown> => {
  const data = record(game.data);
  const itemData = record(data["item_data"]);
  const modifiers = record(data["item_modifiers"]);
  const skin = record(data["item_skin"]);
  const color = itemData["color"];
  return {
    entry: itemData["item_id"],
    count: game.count,
    material: game.id,
    item_data: {
      item_name: itemData["item_name"],
      item_type: itemData["item_type"],
      rarity: itemData["rarity"],
      reforge: itemData["reforge"],
      npc_sell_price: itemData["npc_sell_price"],
      skull: itemData["skull"] === 1,
      ...(typeof color === "string" && color !== "" ? { color } : {}),
      reforge_stats: statSet(itemData["reforge_stats"]),
    },
    item_stats: statSet(data["item_stats"]),
    item_modifiers: {
      hot_potato_books: modifiers["hot_potato_books"],
      golden_hot_potato_books: modifiers["golden_hot_potato_books"],
      final_synchronizers: modifiers["final_synchronizers"],
      art_of_war: modifiers["art_of_war"] === 1,
      recombobulated: modifiers["recombobulated"] === 1,
      star_level: modifiers["star_level"],
    },
    extra: data["extra"] ?? {},
    abilities: record(data["abilities"])["abilities"] ?? [],
    ...(typeof skin["value"] === "string" && skin["value"] !== "" ? { item_skin: { value: skin["value"] } } : {}),
  };
};
