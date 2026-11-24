import { Schema, SchemaAST } from "effect";

/* @keep dragsim.co leaves out a section or sends it as null, and adds keys, so a profile is read leniently. */

const optional = <S extends Schema.Top>(schema: S) => Schema.optional(Schema.NullOr(schema));

const MAPPED = "dsmod/mapped";
const readApart = (shape: Schema.Top) => Schema.Unknown.annotate({ [MAPPED]: shape.ast });
const Scalar = Schema.Union([Schema.String, Schema.Number, Schema.Boolean, Schema.Null]);
const open = (shape: Schema.Top) => Schema.Record(Schema.String, shape);

const Sheet = Schema.Struct({
  base: open(Scalar),
  additive: open(Scalar),
  multiplicative: open(Scalar),
  increase_max: open(Scalar),
});
const Kills = open(Schema.Struct({ kills: Scalar }));
const Item = readApart(
  Schema.Struct({
    entry: Scalar,
    count: Scalar,
    material: Scalar,
    abilities: Schema.Array(Scalar),
    item_skin: Schema.Struct({ value: Scalar }),
    item_stats: Sheet,
    item_data: Schema.Struct({
      item_name: Scalar,
      item_type: Scalar,
      rarity: Scalar,
      reforge: Scalar,
      npc_sell_price: Scalar,
      skull: Scalar,
      color: Scalar,
      reforge_stats: Sheet,
    }),
    item_modifiers: Schema.Struct({
      hot_potato_books: Scalar,
      golden_hot_potato_books: Scalar,
      final_synchronizers: Scalar,
      art_of_war: Scalar,
      recombobulated: Scalar,
      star_level: Scalar,
      rune_id: Scalar,
      enrichment: Scalar,
      kills: Scalar,
    }),
    extra: Schema.Struct({
      item_dye: Schema.Struct({ dyeId: Scalar }),
      item_skin: Schema.Struct({ skinId: Scalar }),
      drill: Schema.Struct({ fuel: Scalar, engine: Scalar, tank: Scalar, module: Scalar, coated: Scalar }),
      dungeon_upgrade: open(Scalar),
      wither_scrolls: open(Scalar),
      coating: open(Scalar),
      attunement: open(Scalar),
    }),
  }),
);

const Slots = Schema.Record(Schema.String, Item);
const WardrobeParts = Schema.Struct({ helmet: Item, chestplate: Item, leggings: Item, boots: Item });

export const Sections = Schema.Struct({
  inventory: optional(Slots),
  ender_chest: optional(Slots),
  storage: optional(Schema.Record(Schema.String, Slots)),
  // @keep The worn wardrobe slot is not in `slots`.
  wardrobe: optional(
    Schema.Struct({
      equipped: optional(Schema.Number),
      slots: optional(Schema.Record(Schema.String, Slots.annotate({ [MAPPED]: WardrobeParts.ast }))),
    }),
  ),
  accessory_bag: optional(Slots),
  pets: optional(
    Schema.Array(
      readApart(
        Schema.Struct({
          type: Scalar,
          rarity: Scalar,
          totalXp: Scalar,
          xpCurve: Scalar,
          candiesUsed: Scalar,
          active: Scalar,
          equippedItem: Scalar,
          skinOverride: Scalar,
        }),
      ),
    ),
  ),
});
export type Sections = typeof Sections.Type;

export const sectionsOf = (profile: Sections): Sections => ({
  inventory: profile.inventory,
  ender_chest: profile.ender_chest,
  storage: profile.storage,
  wardrobe: profile.wardrobe,
  accessory_bag: profile.accessory_bag,
  pets: profile.pets,
});

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
  stats: optional(readApart(Sheet)),
  collections: optional(readApart(open(Scalar))),
  rng_meters: optional(readApart(Kills)),
  dungeons: optional(
    readApart(
      Schema.Struct({
        class: Scalar,
        secrets: Scalar,
        zombie_kills: Scalar,
        skeletor_kills: Scalar,
        class_xp: open(Scalar),
        completions: open(Scalar),
        best_score: open(Scalar),
        essence: open(Scalar),
      }),
    ),
  ),
  hotd: optional(
    readApart(
      Schema.Struct({
        tier: Scalar,
        exp: Scalar,
        core: Scalar,
        ender: Scalar,
        void: Scalar,
        resets: Scalar,
        ability: Scalar,
        tokens: Scalar,
        nodes: open(Scalar),
      }),
    ),
  ),
  forge: optional(
    readApart(
      Schema.Array(Schema.Struct({ slot: Scalar, recipe: Scalar, started: Scalar, duration: Scalar, stage: Scalar })),
    ),
  ),
  skills: optional(readApart(open(Scalar))),
  // @keep `<boss>_xp` and `<boss>_level` for each boss, so any key is a boss.
  slayer: optional(readApart(open(Scalar))),
  bestiary: optional(readApart(Kills)),
});
export type WireProfile = typeof WireProfile.Type;

const Answer = Schema.Struct({
  mode: optional(Schema.String),
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

const members = (ast: SchemaAST.AST): readonly SchemaAST.AST[] =>
  SchemaAST.isUnion(ast) ? ast.types.flatMap(members) : [ast];

export const unmappedPaths = (value: unknown, ast: SchemaAST.AST = Answer.ast, path = ""): readonly string[] => {
  const mapped = ast.annotations?.[MAPPED];
  if (SchemaAST.isAST(mapped)) {
    return unmappedPaths(value, mapped, path);
  }
  if (value === null || typeof value !== "object" || SchemaAST.isUnknown(ast)) {
    return [];
  }
  if (SchemaAST.isUnion(ast)) {
    const fits = Array.isArray(value) ? SchemaAST.isArrays : SchemaAST.isObjects;
    const member = members(ast).find((type) => fits(type) || SchemaAST.isUnknown(type));
    return member === undefined ? [path] : unmappedPaths(value, member, path);
  }
  const found = new Set<string>();
  const add = (paths: readonly string[]) => paths.forEach((each) => found.add(each));
  if (SchemaAST.isArrays(ast)) {
    const element = ast.rest[0];
    if (!Array.isArray(value) || element === undefined) {
      return [path];
    }
    value.forEach((each) => add(unmappedPaths(each, element, `${path}[]`)));
    return [...found];
  }
  if (!SchemaAST.isObjects(ast) || Array.isArray(value)) {
    return [path];
  }
  const index = ast.indexSignatures[0]?.type;
  for (const [key, child] of Object.entries(value)) {
    const known = ast.propertySignatures.find((property) => property.name === key)?.type;
    if (known !== undefined) {
      add(unmappedPaths(child, known, path === "" ? key : `${path}.${key}`));
    } else if (index !== undefined) {
      add(unmappedPaths(child, index, `${path}.*`));
    } else {
      found.add(path === "" ? key : `${path}.${key}`);
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
