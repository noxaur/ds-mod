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
