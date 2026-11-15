import { Schema } from "effect";

/**
 * A run of text in one style. A client draws a style it does not know as plain, so a flag can be added here without
 * breaking an older mod. Colors are `#RRGGBB`, because the server's text uses colors that `§` codes cannot name.
 */
export const Span = Schema.Struct({
  text: Schema.String,
  color: Schema.optionalKey(Schema.String.check(Schema.isPattern(/^#[0-9A-F]{6}$/u))),
  bold: Schema.optionalKey(Schema.Boolean),
  italic: Schema.optionalKey(Schema.Boolean),
  underlined: Schema.optionalKey(Schema.Boolean),
  strikethrough: Schema.optionalKey(Schema.Boolean),
  obfuscated: Schema.optionalKey(Schema.Boolean),
});
export type Span = typeof Span.Type;

/** One line of styled text, as the game draws it. An empty list is an empty line. */
export const Line = Schema.Array(Span);
export type Line = typeof Line.Type;

/** An id with the name to show for it, from the catalog when it has one and else the id in title case. */
export const Named = Schema.Struct({ id: Schema.String, name: Schema.String });
export type Named = typeof Named.Type;

/** One of a character's or an item's stat numbers, named and colored as the game shows the stat. */
export const StatValue = Schema.Struct({
  id: Schema.String,
  /** The catalog's name for the stat, else the id in title case. */
  name: Schema.String,
  /** The stat's color as `#RRGGBB`, the catalog's, else gray. */
  color: Schema.String.check(Schema.isPattern(/^#[0-9A-F]{6}$/u)),
  value: Schema.Number,
});
export type StatValue = typeof StatValue.Type;

/**
 * The four groups the server keeps stats in. How they combine into the number the game shows is not in the data, so
 * a client shows each group as it is. Each list is in the catalog's stat order, and stats the catalog lacks come last.
 */
export const StatSheet = Schema.Struct({
  base: Schema.Array(StatValue),
  additive: Schema.Array(StatValue),
  multiplicative: Schema.Array(StatValue),
  /** `increase_max` upstream. */
  increaseMax: Schema.Array(StatValue),
});
export type StatSheet = typeof StatSheet.Type;

const Scalar = Schema.Union([Schema.String, Schema.Finite, Schema.Boolean]);

/**
 * The structured data behind an item's tooltip, so a client can show a value the tooltip's text only has inside a
 * sentence. Every key is left out when the item has none of it, such as `stars` on a pickaxe. It is read from the
 * server's own data, not rebuilt, so unlike `lore` it is exact.
 */
export const ItemFacts = Schema.Struct({
  /** `item_data.item_type`, such as `SWORD`. Left out for the server's `NONE`. */
  itemType: Schema.optionalKey(Schema.String),
  /** What an NPC pays for one, in coins. */
  npcSellPrice: Schema.optionalKey(Schema.Number),
  /** The reforge's name as the server sends it, such as `Arcane`. */
  reforge: Schema.optionalKey(Schema.String),
  /** Dungeon stars, 0 to 10, where 6 to 10 are the master stars. The larger of `star_level` and `dungeon_upgrade.stars`. */
  stars: Schema.optionalKey(Schema.Int),
  /** `hot_potato_books`. */
  hotPotatoBooks: Schema.optionalKey(Schema.Int),
  /** `golden_hot_potato_books`, the fuming books. */
  fumingBooks: Schema.optionalKey(Schema.Int),
  recombobulated: Schema.optionalKey(Schema.Boolean),
  artOfWar: Schema.optionalKey(Schema.Boolean),
  /** `final_synchronizers`: how many were applied. */
  finalSynchronizers: Schema.optionalKey(Schema.Int),
  rune: Schema.optionalKey(Named),
  /** `enrichment`, such as `magic_find_enrichment`. */
  enrichment: Schema.optionalKey(Named),
  /** The kills the item counted. */
  kills: Schema.optionalKey(Schema.Number),
  /** `item_stats`, the numbers the tooltip's stat lines come from. */
  stats: Schema.optionalKey(StatSheet),
  /** `item_data.reforge_stats`, the part of `stats` the reforge adds. */
  reforgeStats: Schema.optionalKey(StatSheet),
  /** Every ability the item has, hidden ones too, in the server's order. */
  abilities: Schema.optionalKey(Schema.Array(Named)),
  /** The skin the server put on this copy, `extra.item_skin.skinId`. */
  skin: Schema.optionalKey(Named),
  /** `extra.item_dye.dyeId`. `color` has the color it gives. */
  dye: Schema.optionalKey(Named),
  /** `extra.drill`: the fuel in the tank and the parts it is built from. */
  drill: Schema.optionalKey(
    Schema.Struct({
      fuel: Schema.optionalKey(Schema.Number),
      engine: Schema.optionalKey(Named),
      tank: Schema.optionalKey(Named),
      module: Schema.optionalKey(Named),
      coated: Schema.optionalKey(Schema.Boolean),
    }),
  ),
  /** `extra.coating` as the server sends it. Only `{ coated: true }` has been seen. */
  coating: Schema.optionalKey(Schema.Record(Schema.String, Scalar)),
  /** `extra.attunement` as the server sends it. It has only been seen empty, so it is left out until it holds something. */
  attunement: Schema.optionalKey(Schema.Record(Schema.String, Scalar)),
  /** `extra.wither_scrolls`. `value` is the server's number for the scroll, such as `7` for `mask`, and what it counts is unknown. */
  witherScrolls: Schema.optionalKey(Schema.Array(Schema.Struct({ ...Named.fields, value: Schema.Number }))),
  /** `extra.dungeon_upgrade` as the server sends it, such as `{ stars: 6 }`. */
  dungeonUpgrade: Schema.optionalKey(Schema.Record(Schema.String, Schema.Number)),
});
export type ItemFacts = typeof ItemFacts.Type;

/**
 * An item as the game would draw it in a slot, with its tooltip. The backend rebuilds the tooltip, because the
 * server's API sends an item's data but no lore. `material` alone must always draw something, so a client that does
 * not know a newer field such as `itemModel` still shows an item.
 */
export const DrawnItem = Schema.Struct({
  /** The server's id for the item, such as `scylla`, or null for one it gave none. */
  entry: Schema.NullOr(Schema.String),
  /** A Minecraft item id, such as `minecraft:iron_sword`. */
  material: Schema.String,
  /** The model the game draws in place of the material's, when the server gives the item one. */
  itemModel: Schema.optionalKey(Schema.String),
  count: Schema.Int.check(Schema.isBetween({ minimum: 1, maximum: 99 })),
  name: Line,
  lore: Schema.Array(Line),
  /** The server's rarity, such as `LEGENDARY`, for a client that marks it without reading the lore's colors. */
  rarity: Schema.NullOr(Schema.String),
  /**
   * Whether the item glints. The server's API sends no enchantments, so this is the backend's best knowledge: the
   * catalog's, or what enough players saw. An item that glints only sometimes is drawn without it.
   */
  glint: Schema.Boolean,
  /** A player head's texture, the base64 `textures` property, whose skin is on `textures.minecraft.net`. */
  skullTexture: Schema.optionalKey(Schema.String),
  /** A dyed item's color, as `0xRRGGBB`. */
  color: Schema.optionalKey(Schema.Int.check(Schema.isBetween({ minimum: 0, maximum: 0xffffff }))),
  /** The data behind the tooltip. Left out when the item has none, and by a backend older than this field. */
  facts: Schema.optionalKey(ItemFacts),
});
export type DrawnItem = typeof DrawnItem.Type;

/**
 * An item a player saw in the game, sent by a mod whose player agreed to report what it has not seen. Any signed-in
 * client can send anything, so nothing here is drawn until enough different players agree on it. Fields are only ever
 * added, never removed, because the payload is read strictly and an older backend never runs against a newer mod.
 */
export const ItemSighting = Schema.Struct({
  /** `custom_data.item_data.item_id`. */
  entry: Schema.String.check(Schema.isMinLength(1), Schema.isMaxLength(128)),
  material: Schema.String.check(Schema.isMaxLength(128)),
  itemModel: Schema.optionalKey(Schema.String.check(Schema.isMaxLength(128))),
  /** The skin the server put on this copy, `extra.item_skin.skinId`, which changes the texture. */
  skinId: Schema.optionalKey(Schema.String.check(Schema.isMaxLength(128))),
  name: Line,
  lore: Schema.Array(Line).check(Schema.isMaxLength(80)),
  glint: Schema.Boolean,
  skullTexture: Schema.optionalKey(Schema.String.check(Schema.isMaxLength(4096))),
  color: Schema.optionalKey(Schema.Int.check(Schema.isBetween({ minimum: 0, maximum: 0xffffff }))),
  /**
   * The keys of `custom_data` the mod lets through, the ones that say how the item is built and none that name a
   * player or a copy. Open, so a key the server adds reaches the backend without a mod release.
   */
  data: Schema.Record(Schema.String, Schema.Unknown),
});
export type ItemSighting = typeof ItemSighting.Type;

/** What players saw of one item, for an agent improving the tooltips. */
export const ItemAppearance = Schema.Struct({
  entry: Schema.String,
  /** How many different players saw it, and how many of them saw it glint. */
  players: Schema.Int,
  glintPlayers: Schema.Int,
  /** Skull textures by skin, `""` for the item's own, with how many players saw each. */
  textures: Schema.Array(Schema.Struct({ skinId: Schema.String, texture: Schema.String, players: Schema.Int })),
  /** A few sightings as they came, to test the rebuilt tooltip against. */
  samples: Schema.Array(ItemSighting),
});
export type ItemAppearance = typeof ItemAppearance.Type;

/** A page of what players saw. With an `entry` asked for it is that one entry, with its samples. */
export const ItemAppearances = Schema.Struct({
  items: Schema.Array(ItemAppearance),
  /** The `after` that asks for the next page, or null when there is none. */
  next: Schema.NullOr(Schema.String),
});
export type ItemAppearances = typeof ItemAppearances.Type;
