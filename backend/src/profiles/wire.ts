import { Schema } from "effect";

/*
 * What dragsim.co sends, read leniently: a key we do not know is ignored, and a section it leaves out, or sends as
 * null, is none. Only what a profile cannot be drawn without is required. The items stay `unknown`, because the
 * renderer in `items/` is the one that reads them.
 */

const optional = <S extends Schema.Top>(schema: S) => Schema.optional(Schema.NullOr(schema));

/** Items by slot number, as a string key. A slot with no item has no key. */
const Slots = Schema.Record(Schema.String, Schema.Unknown);

/** The sections a container is made of, which a snapshot keeps. */
export const Sections = Schema.Struct({
  inventory: optional(Slots),
  ender_chest: optional(Slots),
  storage: optional(Schema.Record(Schema.String, Slots)),
  /** A wardrobe slot holds up to a helmet, a chestplate, leggings and boots. The worn slot is not in `slots`. */
  wardrobe: optional(
    Schema.Struct({
      equipped: optional(Schema.Number),
      slots: optional(Schema.Record(Schema.String, Schema.Record(Schema.String, Schema.Unknown))),
    }),
  ),
  accessory_bag: optional(Slots),
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
  inventory_hidden: optional(Schema.Boolean),
  /** Total XP by skill. */
  skills: optional(Schema.Record(Schema.String, Schema.Number)),
  /** `zombie_xp` and `zombie_level` for each slayer. */
  slayer: optional(Schema.Record(Schema.String, Schema.Number)),
  bestiary: optional(Schema.Record(Schema.String, Schema.Struct({ kills: optional(Schema.Number) }))),
});
export type WireProfile = typeof WireProfile.Type;

const Answer = Schema.Struct({
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
  /** Missing when the player has no profile. */
  profile: optional(WireProfile),
});

/** An answer that has a profile, which is the only kind the backend keeps. */
export const Found = Schema.Struct({ ...Answer.fields, profile: WireProfile });
export type Found = typeof Found.Type;
export const decodeAnswer = Schema.decodeUnknownEffect(Answer);
export const decodeFound = Schema.decodeUnknownOption(Found);

/** The error text a 404 carries, which tells a missing player from a missing profile. */
export const NotFoundBody = Schema.Struct({ error: Schema.String });

/** The cumulative XP to reach each level, from level 1. */
const Table = Schema.Array(Schema.Number);

export const Progression = Schema.Struct({ default: Table, combat: Table, catacombs: Table });
export type Progression = typeof Progression.Type;

/** The part of `catalog.json` the backend reads itself. The renderer gets the rest as it is. */
export const CatalogShape = Schema.Struct({ progression: Progression });
export const decodeCatalog = Schema.decodeUnknownOption(CatalogShape);
