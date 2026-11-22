import { Schema } from "effect";

/* @keep Read leniently: a key we do not know is ignored, and a section dragsim.co leaves out, or sends as null, is
 * none. Only what a profile cannot be drawn without is required. The items stay `unknown`, because the renderer in
 * `items/` is the one that reads them. */

const optional = <S extends Schema.Top>(schema: S) => Schema.optional(Schema.NullOr(schema));

const Slots = Schema.Record(Schema.String, Schema.Unknown);

export const Sections = Schema.Struct({
  inventory: optional(Slots),
  ender_chest: optional(Slots),
  storage: optional(Schema.Record(Schema.String, Slots)),
  // @keep The worn wardrobe slot is not in `slots`.
  wardrobe: optional(
    Schema.Struct({
      equipped: optional(Schema.Number),
      slots: optional(Schema.Record(Schema.String, Schema.Record(Schema.String, Schema.Unknown))),
    }),
  ),
  accessory_bag: optional(Slots),
  pets: optional(Schema.Array(Schema.Unknown)),
});
export type Sections = typeof Sections.Type;

export const WireProfile = Schema.Struct({
  ...Sections.fields,
  id: Schema.String,
  profile_name: Schema.String,
  profile_mode: Schema.String,
  coins: optional(Schema.Number),
  motes: optional(Schema.Number),
  bits: optional(Schema.Number),
  dragon_essence: optional(Schema.Number),
  accessory_power: optional(Schema.String),
  haunted_candy_earned: optional(Schema.Number),
  draconic_experience: optional(Schema.Number),
  updated_at: optional(Schema.Number),
  inventory_hidden: optional(Schema.Boolean),
  stats: optional(Schema.Unknown),
  collections: optional(Schema.Unknown),
  rng_meters: optional(Schema.Unknown),
  dungeons: optional(Schema.Unknown),
  hotd: optional(Schema.Unknown),
  forge: optional(Schema.Unknown),
  skills: optional(Schema.Unknown),
  slayer: optional(Schema.Unknown),
  bestiary: optional(Schema.Unknown),
});
export type WireProfile = typeof WireProfile.Type;

const Answer = Schema.Struct({
  // @keep `mode` is always the `main` the backend asked for, so it is not passed on.
  mode: optional(Schema.String),
  // @keep `fetchedAt` is when the backend read the answer, so the server's own time is not passed on.
  fetched_at: optional(Schema.Number),
  player: Schema.Struct({
    uuid: Schema.String,
    player: Schema.String,
    rank: optional(Schema.String),
  }),
  profiles: optional(
    Schema.Array(
      Schema.Struct({
        id: Schema.String,
        name: Schema.String,
        mode: Schema.String,
        selected: optional(Schema.Boolean),
      }),
    ),
  ),
  profile: optional(WireProfile),
});

/** @keep What the backend reads of an answer, down to every field, so a field dragsim.co adds at any depth shows in the
 * log. A leaf is a string, number or boolean the backend passes on, so a map or a list where one was expected is named
 * too. An open map takes any key, such as a slot number or a mob's id, and reads each value with one shape. */
type Shape = true | { readonly open: Shape } | { readonly list: Shape } | { readonly [key: string]: Shape };
const LEAF = true;
const open = (shape: Shape): Shape => ({ open: shape });
const list = (shape: Shape): Shape => ({ list: shape });
const isOpen = (shape: Shape): shape is { readonly open: Shape } => shape !== true && "open" in shape;
const isList = (shape: Shape): shape is { readonly list: Shape } => shape !== true && "list" in shape;

const SHEET: Shape = { base: open(LEAF), additive: open(LEAF), multiplicative: open(LEAF), increase_max: open(LEAF) };
const ITEM: Shape = {
  entry: LEAF,
  count: LEAF,
  material: LEAF,
  abilities: list(LEAF),
  item_skin: { value: LEAF },
  item_stats: SHEET,
  item_data: {
    item_name: LEAF,
    item_type: LEAF,
    rarity: LEAF,
    reforge: LEAF,
    npc_sell_price: LEAF,
    skull: LEAF,
    color: LEAF,
    reforge_stats: SHEET,
  },
  item_modifiers: {
    hot_potato_books: LEAF,
    golden_hot_potato_books: LEAF,
    final_synchronizers: LEAF,
    art_of_war: LEAF,
    recombobulated: LEAF,
    star_level: LEAF,
    rune_id: LEAF,
    enrichment: LEAF,
    kills: LEAF,
  },
  extra: {
    item_dye: { dyeId: LEAF },
    item_skin: { skinId: LEAF },
    drill: { fuel: LEAF, engine: LEAF, tank: LEAF, module: LEAF, coated: LEAF },
    // @keep Passed on whole, as `facts`.
    dungeon_upgrade: open(LEAF),
    wither_scrolls: open(LEAF),
    coating: open(LEAF),
    attunement: open(LEAF),
  },
};
const KILLS: Shape = { kills: LEAF };

export const ANSWER_SHAPE: Shape = {
  // @keep Our own request echoed back, and a time older than ours: read, and not passed on.
  mode: LEAF,
  fetched_at: LEAF,
  player: { uuid: LEAF, player: LEAF, rank: LEAF },
  profiles: list({ id: LEAF, name: LEAF, mode: LEAF, selected: LEAF }),
  profile: {
    id: LEAF,
    profile_name: LEAF,
    profile_mode: LEAF,
    coins: LEAF,
    motes: LEAF,
    bits: LEAF,
    haunted_candy_earned: LEAF,
    dragon_essence: LEAF,
    draconic_experience: LEAF,
    accessory_power: LEAF,
    updated_at: LEAF,
    inventory_hidden: LEAF,
    stats: SHEET,
    skills: open(LEAF),
    bestiary: open(KILLS),
    collections: open(LEAF),
    rng_meters: open(KILLS),
    // @keep `<boss>_xp` and `<boss>_level` for each boss, so any key is a boss.
    slayer: open(LEAF),
    dungeons: {
      class: LEAF,
      secrets: LEAF,
      zombie_kills: LEAF,
      skeletor_kills: LEAF,
      class_xp: open(LEAF),
      completions: open(LEAF),
      best_score: open(LEAF),
      essence: open(LEAF),
    },
    hotd: {
      tier: LEAF,
      exp: LEAF,
      core: LEAF,
      ender: LEAF,
      void: LEAF,
      resets: LEAF,
      ability: LEAF,
      tokens: LEAF,
      nodes: open(LEAF),
    },
    inventory: open(ITEM),
    ender_chest: open(ITEM),
    accessory_bag: open(ITEM),
    storage: open(open(ITEM)),
    wardrobe: { equipped: LEAF, slots: open({ helmet: ITEM, chestplate: ITEM, leggings: ITEM, boots: ITEM }) },
    pets: list({
      type: LEAF,
      rarity: LEAF,
      totalXp: LEAF,
      xpCurve: LEAF,
      candiesUsed: LEAF,
      active: LEAF,
      equippedItem: LEAF,
      skinOverride: LEAF,
    }),
    forge: list({ slot: LEAF, recipe: LEAF, started: LEAF, duration: LEAF, stage: LEAF }),
  },
};

/** @keep Paths look like `profile.dungeons.new_section`. An open map's key is `*` and a list's element is `[]`, so a
 * path is named once however many slots or mobs have it. */
export const unmappedPaths = (value: unknown, shape: Shape = ANSWER_SHAPE, path = ""): readonly string[] => {
  if (value === null || typeof value !== "object") {
    return [];
  }
  if (shape === true) {
    return [path];
  }
  const found = new Set<string>();
  const add = (paths: readonly string[]) => paths.forEach((each) => found.add(each));
  if (isList(shape)) {
    // @keep A map where a list was expected is read as none, so it is named.
    if (!Array.isArray(value)) {
      return [path];
    }
    value.forEach((element) => add(unmappedPaths(element, shape.list, `${path}[]`)));
    return [...found];
  }
  for (const [key, child] of Object.entries(value)) {
    if (isOpen(shape)) {
      add(unmappedPaths(child, shape.open, `${path}.*`));
      continue;
    }
    const known = Object.hasOwn(shape, key) ? shape[key] : undefined;
    const at = path === "" ? key : `${path}.${key}`;
    if (known === undefined) {
      found.add(at);
    } else {
      add(unmappedPaths(child, known, at));
    }
  }
  return [...found];
};

export const Found = Schema.Struct({ ...Answer.fields, profile: WireProfile });
export type Found = typeof Found.Type;
export const decodeAnswer = Schema.decodeUnknownEffect(Answer);
export const decodeFound = Schema.decodeUnknownOption(Found);

export const NotFoundBody = Schema.Struct({ error: Schema.String });

const Table = Schema.Array(Schema.Number);

export const Progression = Schema.Struct({ default: Table, combat: Table, catacombs: Table });
export type Progression = typeof Progression.Type;

export const CatalogShape = Schema.Struct({ progression: Progression });
export const decodeCatalog = Schema.decodeUnknownOption(CatalogShape);
