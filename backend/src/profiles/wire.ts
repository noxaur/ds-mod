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
  /** Each pet as the server sends it. They are read leniently by `pets.ts`. */
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
  /** When the server saved the profile, in epoch milliseconds. */
  updated_at: optional(Schema.Number),
  inventory_hidden: optional(Schema.Boolean),
  /*
   * The sections below are kept as the server sends them and read leniently by `sections.ts`, so a change to one of
   * them costs that section and never the whole profile.
   */
  /** `{ base, additive, multiplicative, increase_max }`, each a record of stat to number. */
  stats: optional(Schema.Unknown),
  collections: optional(Schema.Unknown),
  rng_meters: optional(Schema.Unknown),
  dungeons: optional(Schema.Unknown),
  hotd: optional(Schema.Unknown),
  forge: optional(Schema.Unknown),
  /** Total XP by skill. */
  skills: optional(Schema.Record(Schema.String, Schema.Number)),
  /** `zombie_xp` and `zombie_level` for each slayer. */
  slayer: optional(Schema.Record(Schema.String, Schema.Number)),
  bestiary: optional(Schema.Record(Schema.String, Schema.Struct({ kills: optional(Schema.Number) }))),
});
export type WireProfile = typeof WireProfile.Type;

const Answer = Schema.Struct({
  /** The `mode` the backend asked for, which is always `main`, so it says nothing the request did not. Not passed on. */
  mode: optional(Schema.String),
  /** When the server built this answer, in epoch milliseconds. `fetchedAt` is when the backend read it, so this is not passed on. */
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
  /** Missing when the player has no profile. */
  profile: optional(WireProfile),
});

/** The keys of the answer and of its profile that the backend maps, so a key the server adds shows in the log. */
export const MAPPED_KEYS = {
  answer: Object.keys(Answer.fields),
  profile: Object.keys(WireProfile.fields),
};

const keysOf = (value: unknown): readonly string[] =>
  typeof value === "object" && value !== null && !Array.isArray(value) ? Object.keys(value) : [];

/** The keys of an answer, as `profile.<key>` inside the profile, that `MAPPED_KEYS` does not have. */
export const unmappedKeys = (body: unknown): readonly string[] => {
  const profile: unknown = typeof body === "object" && body !== null ? Reflect.get(body, "profile") : undefined;
  return [
    ...keysOf(body).filter((key) => !MAPPED_KEYS.answer.includes(key)),
    ...keysOf(profile)
      .filter((key) => !MAPPED_KEYS.profile.includes(key))
      .map((key) => `profile.${key}`),
  ];
};

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
