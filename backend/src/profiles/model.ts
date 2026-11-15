import { Schema } from "effect";

import { DrawnItem, Line, Named, StatSheet, StatValue } from "../items/model.ts";

/** A Minecraft username, which is all dragsim.co looks a player up by. */
export const PlayerName = Schema.String.check(Schema.isPattern(/^[A-Za-z0-9_]{1,16}$/u));

/** A profile's id from the player's list of profiles, which is not their Minecraft UUID. */
export const ProfileId = Schema.String.check(Schema.isPattern(/^[0-9a-f-]{36}$/u));

/**
 * Something with levels: a skill or a slayer. dragsim.co sends only the total, and the backend does the level math, so
 * a client shows these as they are. `xpForNextLevel` is null at the cap, and when the backend does not know the table.
 */
export const Progress = Schema.Struct({
  id: Schema.String,
  name: Schema.String,
  level: Schema.Int,
  maxLevel: Schema.Int,
  totalXp: Schema.Number,
  xpIntoLevel: Schema.Number,
  xpForNextLevel: Schema.NullOr(Schema.Number),
});

/** A number the profile shows, formatted as the game shows it, so a new one needs no client change. */
export const Stat = Schema.Struct({ id: Schema.String, name: Schema.String, value: Line });

/**
 * A grid of items the profile has, such as the inventory or a storage page. `id` is stable, so a client may give a
 * known one its own place, and must show one it does not know as a plain grid. A container the player hid is listed
 * with `hidden`, so a client can say so instead of drawing it empty.
 */
export const ContainerSummary = Schema.Struct({
  id: Schema.String,
  title: Line,
  columns: Schema.Int.check(Schema.isBetween({ minimum: 1, maximum: 9 })),
  slots: Schema.Int.check(Schema.isGreaterThanOrEqualTo(0)),
  hidden: Schema.Boolean,
});

export const Container = Schema.Struct({
  ...ContainerSummary.fields,
  /** One entry per slot, in reading order, null where the slot is empty. Empty when hidden. */
  items: Schema.Array(Schema.NullOr(DrawnItem)),
});
export type Container = typeof Container.Type;

const Slot = Schema.NullOr(DrawnItem);

/** A mob of the bestiary. */
export const Kills = Schema.Struct({
  /** The server's id, such as `NEST_ZEALOT`. */
  id: Schema.String,
  name: Schema.String,
  kills: Schema.Int,
});

/** A pet the player owns. */
export const Pet = Schema.Struct({
  /** The server's species id, such as `GOLDEN_DRAGON`. */
  type: Schema.String,
  /** The catalog's display name for the species, else the id in title case. */
  name: Schema.String,
  /** The pet's rarity, such as `LEGENDARY`. */
  rarity: Schema.String,
  /** Raw pet XP. */
  totalXp: Schema.Number,
  /**
   * `xpCurve` as the server sends it. It was `2` on every pet seen, so which table of levels it picks is unknown,
   * and `level` stays null until the backend has one.
   */
  xpCurve: Schema.Number,
  /** How many pet candies were used. */
  candiesUsed: Schema.Int,
  /** Whether the pet is the one summoned. */
  active: Schema.Boolean,
  /** The pet item it holds, `equippedItem` upstream, named from the catalog's items. Null when it holds none. */
  heldItem: Schema.NullOr(Named),
  /** The skin it wears, `skinOverride` upstream, named from the catalog's items. Null when it wears none. */
  skinOverride: Schema.NullOr(Named),
  /** The pet's level. Null always for now: neither the data nor the catalog has a pet XP table. */
  level: Schema.NullOr(Schema.Int),
  /** The species' highest level from the catalog, or null when the catalog lacks the species. */
  maxLevel: Schema.NullOr(Schema.Int),
  /** The catalog's category of the species, such as `Combat`, or null. */
  category: Schema.NullOr(Schema.String),
  /** What each level of the species adds. `value` is per level, not a total. */
  perks: Schema.Array(StatValue),
  /** The catalog's description of the species, or none. */
  description: Schema.Array(Line),
  /**
   * The pet drawn for a grid: its head, its name in the rarity's color, and a lore of what is known. This is rebuilt
   * by the backend and is not the server's own tooltip, which the API does not send.
   */
  item: DrawnItem,
});
export type Pet = typeof Pet.Type;

export const Dungeons = Schema.Struct({
  /** The class the player has selected, such as `TANK`, or null when the server names none. */
  selectedClass: Schema.NullOr(Named),
  /** Secrets found. */
  secrets: Schema.Number,
  /** `zombie_kills` upstream. */
  zombieKills: Schema.Number,
  /** `skeletor_kills` upstream. */
  skeletorKills: Schema.Number,
  /**
   * The XP of each class, leveled by the catalog's `catacombs` table, the same one the Catacombs skill uses, because
   * the numbers fit it. It is the table of the game this server copies, and the data does not say so.
   */
  classes: Schema.Array(Progress),
  /** Floors with a completion or a score, `F7` before `M1`. */
  floors: Schema.Array(
    Schema.Struct({
      /** The server's id, such as `F7` or `M3`. */
      id: Schema.String,
      /** `Floor 7`, `Master Mode 3`, or the id in title case for one of another kind. */
      name: Schema.String,
      completions: Schema.Number,
      /** The best score, or null when the player has none for the floor. */
      bestScore: Schema.NullOr(Schema.Number),
    }),
  ),
  /** The essences the player holds, such as `wither_essence`. */
  essence: Schema.Array(Schema.Struct({ id: Schema.String, name: Schema.String, amount: Schema.Number })),
});

/** The Heart of the Dragon, which the server calls `hotd`. */
export const Hotd = Schema.Struct({
  /** The server's `tier`. */
  tier: Schema.Number,
  /** The server's `exp`: raw XP. No table of what it means for a tier is known. */
  exp: Schema.Number,
  /** The server's `core`. A number such as 10 beside `tier`. What it counts is unknown, so it is passed on as it is. */
  core: Schema.Number,
  /** The server's `ender`: an amount that grows large. Probably a powder, as the Heart of the Mountain has, but unproven. */
  ender: Schema.Number,
  /** The server's `void`: a second amount like `ender`. */
  void: Schema.Number,
  /** The server's `resets`: probably how many times the player reset the Heart, unproven. */
  resets: Schema.Number,
  /** The ability the player chose, such as `void_infusion`, or null. */
  ability: Schema.NullOr(Named),
  /** The server's `tokens`: probably the unspent points for nodes, unproven. */
  tokens: Schema.Number,
  /** Every node and its level, in the server's order. The names are the ids in title case. */
  nodes: Schema.Array(Schema.Struct({ id: Schema.String, name: Schema.String, level: Schema.Number })),
});

/** One slot of the forge. */
export const ForgeSlot = Schema.Struct({
  slot: Schema.Int,
  /** The server's recipe id, such as `refined_end_stone`. */
  recipe: Schema.String,
  /** The catalog item's name for the recipe, else the id in title case. */
  name: Schema.String,
  /** When it was started, in epoch milliseconds. */
  startedAt: Schema.Number,
  /** How long it takes, in milliseconds. */
  durationMs: Schema.Number,
  /** `startedAt + durationMs`, in epoch milliseconds. */
  finishesAt: Schema.Number,
  /** The server's stage, such as `READY`. A client shows one it does not know as it is. */
  stage: Schema.String,
});

/**
 * One player's profile, as the overview draws it. The containers are listed but not filled, because rebuilding every
 * tooltip of thirty storage pages for a player who opens one tab is wasted work. A client fetches a container from
 * the same `snapshot`, so every tab shows the profile as it was at one moment.
 */
export const Profile = Schema.Struct({
  /** Names this copy of the profile. Pass it to fetch a container. It expires, and then the profile is fetched again. */
  snapshot: Schema.String,
  /** When the backend read this from dragsim.co, as an ISO time. */
  fetchedAt: Schema.String,
  /** True when dragsim.co could not be reached and this is the last copy the backend had. */
  stale: Schema.Boolean,
  player: Schema.Struct({
    uuid: Schema.String,
    name: Schema.String,
    /** `id` is the server's, such as `MVP_PLUS`, and `prefix` is how the game shows it before a name. */
    rank: Schema.Struct({ id: Schema.String, prefix: Line }),
  }),
  profiles: Schema.Array(
    Schema.Struct({
      id: Schema.String,
      name: Line,
      /** Such as `NORMAL` or `IRONMAN`. A client shows one it does not know as it is. */
      mode: Schema.String,
      selected: Schema.Boolean,
    }),
  ),
  /** Which of `profiles` this is. */
  profileId: Schema.String,
  skills: Schema.Array(Progress),
  slayers: Schema.Array(Progress),
  stats: Schema.Array(Stat),
  /** The bestiary's mobs with the most kills, most first. */
  topKills: Schema.Array(Kills),
  /** When the server saved the profile as an ISO time, or null when it did not say. */
  savedAt: Schema.NullOr(Schema.String),
  /** The wardrobe slot that is worn, a key of `wardrobe:*` counted from 0, or null when none is. */
  equippedWardrobeSlot: Schema.NullOr(Schema.Int),
  /** The player's stats in the four groups the server keeps them in. */
  playerStats: StatSheet,
  /** Every mob of the bestiary, most kills first, with the ones the player never killed. */
  bestiary: Schema.Array(Kills),
  /** Each collection's amount, named from the catalog's items, in the server's order. */
  collections: Schema.Array(Schema.Struct({ id: Schema.String, name: Schema.String, amount: Schema.Number })),
  /**
   * The RNG meters. `progress` is the `kills` the server keeps for the meter, in the meter's own unit, so the
   * `mine_` ones count blocks. The names are the ids in title case.
   */
  rngMeters: Schema.Array(Schema.Struct({ id: Schema.String, name: Schema.String, progress: Schema.Number })),
  /** Null when the server sends no dungeons. */
  dungeons: Schema.NullOr(Dungeons),
  /** Null when the server sends no Heart of the Dragon. */
  hotd: Schema.NullOr(Hotd),
  /** Every pet in the server's order. The `pets` container draws the same ones in a grid. */
  pets: Schema.Array(Pet),
  forge: Schema.Array(ForgeSlot),
  /** What the player wears and holds, for the model. Null where nothing is, or where the player hid it. */
  equipment: Schema.Struct({ helmet: Slot, chestplate: Slot, leggings: Slot, boots: Slot, held: Slot }),
  containers: Schema.Array(ContainerSummary),
});
export type Profile = typeof Profile.Type;

/*
 * The failures a client can tell apart. Each has a stable `_tag` in its body, and a client that gets no body, as from
 * a proxy, falls back on the status alone.
 */
export class PlayerNotFound extends Schema.TaggedError<PlayerNotFound>()(
  "PlayerNotFound",
  {},
  { httpApiStatus: 404, description: "dragsim.co knows no player by that name." },
) {}

export class ProfileNotFound extends Schema.TaggedError<ProfileNotFound>()(
  "ProfileNotFound",
  {},
  { httpApiStatus: 404, description: "The player has no profile, or none with that id." },
) {}

export class ContainerNotFound extends Schema.TaggedError<ContainerNotFound>()(
  "ContainerNotFound",
  {},
  { httpApiStatus: 404, description: "The snapshot has no container with that id." },
) {}

export class SnapshotExpired extends Schema.TaggedError<SnapshotExpired>()(
  "SnapshotExpired",
  {},
  { httpApiStatus: 410, description: "The snapshot is gone. Fetch the profile again for a new one." },
) {}

export class UpstreamUnavailable extends Schema.TaggedError<UpstreamUnavailable>()(
  "UpstreamUnavailable",
  {},
  { httpApiStatus: 503, description: "dragsim.co did not answer, and the backend has no copy to fall back on." },
) {}
