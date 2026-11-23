import { Schema } from "effect";

/** @keep Colors are `#RRGGBB`, because the server's text uses colors that `§` codes cannot name. */
export const Span = Schema.Struct({
  text: Schema.String,
  color: Schema.optionalKey(Schema.String.check(Schema.isPattern(/^#[0-9A-F]{6}$/u))),
  bold: Schema.optionalKey(Schema.Boolean),
  italic: Schema.optionalKey(Schema.Boolean),
  underlined: Schema.optionalKey(Schema.Boolean),
  strikethrough: Schema.optionalKey(Schema.Boolean),
  obfuscated: Schema.optionalKey(Schema.Boolean),
  font: Schema.optionalKey(Schema.String.check(Schema.isPattern(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/u))),
});
export type Span = typeof Span.Type;

export const Line = Schema.Array(Span);
export type Line = typeof Line.Type;

export const Named = Schema.Struct({ id: Schema.String, name: Schema.String });
export type Named = typeof Named.Type;

export const StatValue = Schema.Struct({
  id: Schema.String,
  name: Schema.String,
  color: Schema.String.check(Schema.isPattern(/^#[0-9A-F]{6}$/u)),
  value: Schema.Number,
});
export type StatValue = typeof StatValue.Type;

/** @keep How the groups combine into the number the game shows is not in the data, so a client shows each as it is. */
export const StatSheet = Schema.Struct({
  base: Schema.Array(StatValue),
  additive: Schema.Array(StatValue),
  multiplicative: Schema.Array(StatValue),
  increaseMax: Schema.Array(StatValue),
});
export type StatSheet = typeof StatSheet.Type;

const Scalar = Schema.Union([Schema.String, Schema.Finite, Schema.Boolean]);

export const ItemFacts = Schema.Struct({
  // @keep Left out for the server's `NONE`.
  itemType: Schema.optionalKey(Schema.String),
  /** @keep The server's word on whether the item is a head. It can disagree with the material: a helmet drawn as a
   * `player_head` from a skin says false. */
  skull: Schema.optionalKey(Schema.Boolean),
  npcSellPrice: Schema.optionalKey(Schema.Number),
  reforge: Schema.optionalKey(Schema.String),
  // @keep Dungeon stars, 0 to 10, where 6 to 10 are the master stars.
  stars: Schema.optionalKey(Schema.Int),
  hotPotatoBooks: Schema.optionalKey(Schema.Int),
  fumingBooks: Schema.optionalKey(Schema.Int),
  recombobulated: Schema.optionalKey(Schema.Boolean),
  artOfWar: Schema.optionalKey(Schema.Boolean),
  finalSynchronizers: Schema.optionalKey(Schema.Int),
  rune: Schema.optionalKey(Named),
  enrichment: Schema.optionalKey(Named),
  kills: Schema.optionalKey(Schema.Number),
  stats: Schema.optionalKey(StatSheet),
  reforgeStats: Schema.optionalKey(StatSheet),
  abilities: Schema.optionalKey(Schema.Array(Named)),
  skin: Schema.optionalKey(Named),
  dye: Schema.optionalKey(Named),
  drill: Schema.optionalKey(
    Schema.Struct({
      fuel: Schema.optionalKey(Schema.Number),
      engine: Schema.optionalKey(Named),
      tank: Schema.optionalKey(Named),
      module: Schema.optionalKey(Named),
      coated: Schema.optionalKey(Schema.Boolean),
    }),
  ),
  // @keep Only `{ coated: true }` has been seen.
  coating: Schema.optionalKey(Schema.Record(Schema.String, Scalar)),
  // @keep It has only been seen empty, so it is left out until it holds something.
  attunement: Schema.optionalKey(Schema.Record(Schema.String, Scalar)),
  // @keep `value` is the server's number for the scroll, such as `7` for `mask`, and what it counts is unknown.
  witherScrolls: Schema.optionalKey(Schema.Array(Schema.Struct({ ...Named.fields, value: Schema.Number }))),
  dungeonUpgrade: Schema.optionalKey(Schema.Record(Schema.String, Schema.Number)),
});
export type ItemFacts = typeof ItemFacts.Type;

/** @keep The server's API sends an item's data but no lore, so the backend rebuilds the tooltip. */
export const DrawnItem = Schema.Struct({
  entry: Schema.NullOr(Schema.String),
  material: Schema.String,
  itemModel: Schema.optionalKey(Schema.String),
  count: Schema.Int.check(Schema.isBetween({ minimum: 1, maximum: 99 })),
  name: Line,
  lore: Schema.Array(Line),
  rarity: Schema.NullOr(Schema.String),
  /** @keep The server's API sends no enchantments, so this is the catalog's word or what enough players saw. */
  glint: Schema.Boolean,
  skullTexture: Schema.optionalKey(Schema.String),
  color: Schema.optionalKey(Schema.Int.check(Schema.isBetween({ minimum: 0, maximum: 0xffffff }))),
  facts: Schema.optionalKey(ItemFacts),
  customData: Schema.optionalKey(Schema.Record(Schema.String, Schema.Unknown)),
});
export type DrawnItem = typeof DrawnItem.Type;

/** @keep The longest span in the captures is 224 characters, and the most spans on one line is 10. */
export const MAX_SPAN_TEXT = 1024;
export const MAX_LINE_SPANS = 64;
export const MAX_DATA_CHARS = 16_384;

const SightedLine = Schema.Array(
  Span.mapFields((fields) => ({ ...fields, text: fields.text.check(Schema.isMaxLength(MAX_SPAN_TEXT)) })),
).check(Schema.isMaxLength(MAX_LINE_SPANS));

/** @keep Any signed-in client can send anything, so nothing here is drawn until enough players agree on it. Fields are
 * only ever added, because the payload is read strictly and an older backend never runs against a newer mod. */
export const ItemSighting = Schema.Struct({
  entry: Schema.String.check(Schema.isMinLength(1), Schema.isMaxLength(128)),
  material: Schema.String.check(Schema.isMaxLength(128)),
  itemModel: Schema.optionalKey(Schema.String.check(Schema.isMaxLength(128))),
  skinId: Schema.optionalKey(Schema.String.check(Schema.isMaxLength(128))),
  name: SightedLine,
  lore: Schema.Array(SightedLine).check(Schema.isMaxLength(80)),
  glint: Schema.Boolean,
  skullTexture: Schema.optionalKey(Schema.String.check(Schema.isMaxLength(4096))),
  color: Schema.optionalKey(Schema.Int.check(Schema.isBetween({ minimum: 0, maximum: 0xffffff }))),
  /** @keep The keys of `custom_data` the mod lets through, none that name a player or a copy. Open, so a key the server
   * adds reaches the backend without a mod release. The most the captures hold is 885 characters as JSON. */
  data: Schema.Record(Schema.String.check(Schema.isMaxLength(64)), Schema.Unknown).check(
    Schema.makeFilter(
      (data) =>
        JSON.stringify(data).length <= MAX_DATA_CHARS || `Expected at most ${MAX_DATA_CHARS} characters of JSON`,
    ),
  ),
});
export type ItemSighting = typeof ItemSighting.Type;

export const ItemAppearance = Schema.Struct({
  entry: Schema.String,
  players: Schema.Int,
  glintPlayers: Schema.Int,
  textures: Schema.Array(Schema.Struct({ skinId: Schema.String, texture: Schema.String, players: Schema.Int })),
  samples: Schema.Array(ItemSighting),
});
export type ItemAppearance = typeof ItemAppearance.Type;

export const ItemAppearances = Schema.Struct({
  items: Schema.Array(ItemAppearance),
  next: Schema.NullOr(Schema.String),
});
export type ItemAppearances = typeof ItemAppearances.Type;
