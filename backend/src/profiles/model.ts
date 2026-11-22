import { Schema } from "effect";

import { DrawnItem, Line, Named, StatSheet, StatValue } from "../items/model.ts";

export const PlayerName = Schema.String.check(Schema.isPattern(/^[A-Za-z0-9_]{1,16}$/u));

export const ProfileId = Schema.String.check(Schema.isPattern(/^[0-9a-f-]{36}$/u));

/** @keep dragsim.co sends only the total, and the backend does the level math, so a client shows these as they are.
 * `xpForNextLevel` is null at the cap, and when the backend does not know the table. */
export const Progress = Schema.Struct({
  id: Schema.String,
  name: Schema.String,
  level: Schema.Int,
  maxLevel: Schema.Int,
  totalXp: Schema.Number,
  xpIntoLevel: Schema.Number,
  xpForNextLevel: Schema.NullOr(Schema.Number),
});

export const Stat = Schema.Struct({ id: Schema.String, name: Schema.String, value: Line });

/** @keep `id` is stable, so a client may give a known one its own place, and must show one it does not know as a plain
 * grid. A container the player hid is listed with `hidden`, so a client can say so instead of drawing it empty. */
export const ContainerSummary = Schema.Struct({
  id: Schema.String,
  title: Line,
  columns: Schema.Int.check(Schema.isBetween({ minimum: 1, maximum: 9 })),
  slots: Schema.Int.check(Schema.isGreaterThanOrEqualTo(0)),
  hidden: Schema.Boolean,
});

export const Container = Schema.Struct({
  ...ContainerSummary.fields,
  items: Schema.Array(Schema.NullOr(DrawnItem)),
});
export type Container = typeof Container.Type;

const Slot = Schema.NullOr(DrawnItem);

export const Kills = Schema.Struct({
  id: Schema.String,
  name: Schema.String,
  kills: Schema.Int,
});

export const Pet = Schema.Struct({
  type: Schema.String,
  name: Schema.String,
  rarity: Schema.String,
  totalXp: Schema.Number,
  xpCurve: Schema.Number,
  candiesUsed: Schema.Int,
  active: Schema.Boolean,
  heldItem: Schema.NullOr(Named),
  skinOverride: Schema.NullOr(Named),
  level: Schema.Int,
  maxLevel: Schema.Int,
  xpIntoLevel: Schema.Number,
  xpForNextLevel: Schema.NullOr(Schema.Number),
  category: Schema.NullOr(Schema.String),
  // @keep `value` is per level, not a total.
  perks: Schema.Array(StatValue),
  description: Schema.Array(Line),
  /** @keep Rebuilt by the backend, not the server's own tooltip, which the API does not send. */
  item: DrawnItem,
});
export type Pet = typeof Pet.Type;

export const Dungeons = Schema.Struct({
  selectedClass: Schema.NullOr(Named),
  secrets: Schema.Number,
  zombieKills: Schema.Number,
  skeletorKills: Schema.Number,
  /** @keep Leveled by the catalog's `catacombs` table, the same one the Catacombs skill uses, because the numbers fit
   * it. It is the table of the game this server copies, and the data does not say so. */
  classes: Schema.Array(Progress),
  floors: Schema.Array(
    Schema.Struct({
      id: Schema.String,
      name: Schema.String,
      completions: Schema.Number,
      bestScore: Schema.NullOr(Schema.Number),
    }),
  ),
  essence: Schema.Array(Schema.Struct({ id: Schema.String, name: Schema.String, amount: Schema.Number })),
});

export const Hotd = Schema.Struct({
  tier: Schema.Number,
  // @keep No table of what `exp` means for a tier is known.
  exp: Schema.Number,
  // @keep What `core` counts is unknown, so it is passed on as it is.
  core: Schema.Number,
  // @keep `ender` is probably a powder, as the Heart of the Mountain has, but unproven.
  ender: Schema.Number,
  void: Schema.Number,
  // @keep `resets` is probably how many times the player reset the Heart, unproven.
  resets: Schema.Number,
  ability: Schema.NullOr(Named),
  // @keep `tokens` is probably the unspent points for nodes, unproven.
  tokens: Schema.Number,
  nodes: Schema.Array(Schema.Struct({ id: Schema.String, name: Schema.String, level: Schema.Number })),
});

export const ForgeSlot = Schema.Struct({
  slot: Schema.Int,
  recipe: Schema.String,
  name: Schema.String,
  startedAt: Schema.Number,
  durationMs: Schema.Number,
  finishesAt: Schema.Number,
  stage: Schema.String,
});

/** @keep The containers are listed but not filled, because rebuilding every tooltip of thirty storage pages for a
 * player who opens one tab is wasted work. A client fetches a container from the same `snapshot`, so every tab shows
 * the profile as it was at one moment. */
export const Profile = Schema.Struct({
  // @keep The snapshot expires, and then the profile is fetched again.
  snapshot: Schema.String,
  fetchedAt: Schema.String,
  stale: Schema.Boolean,
  player: Schema.Struct({
    uuid: Schema.String,
    name: Schema.String,
    rank: Schema.Struct({ id: Schema.String, prefix: Line }),
  }),
  profiles: Schema.Array(
    Schema.Struct({
      id: Schema.String,
      name: Line,
      mode: Schema.String,
      selected: Schema.Boolean,
    }),
  ),
  profileId: Schema.String,
  profileName: Line,
  profileMode: Schema.String,
  skills: Schema.Array(Progress),
  slayers: Schema.Array(Progress),
  stats: Schema.Array(Stat),
  topKills: Schema.Array(Kills),
  savedAt: Schema.NullOr(Schema.String),
  equippedWardrobeSlot: Schema.NullOr(Schema.Int),
  playerStats: StatSheet,
  bestiary: Schema.Array(Kills),
  collections: Schema.Array(Schema.Struct({ id: Schema.String, name: Schema.String, amount: Schema.Number })),
  /** @keep `progress` is the `kills` the server keeps for the meter, in the meter's own unit, so the `mine_` ones count
   * blocks. */
  rngMeters: Schema.Array(Schema.Struct({ id: Schema.String, name: Schema.String, progress: Schema.Number })),
  dungeons: Schema.NullOr(Dungeons),
  hotd: Schema.NullOr(Hotd),
  pets: Schema.Array(Pet),
  forge: Schema.Array(ForgeSlot),
  equipment: Schema.Struct({ helmet: Slot, chestplate: Slot, leggings: Slot, boots: Slot, held: Slot }),
  containers: Schema.Array(ContainerSummary),
});
export type Profile = typeof Profile.Type;

/* @keep Each failure has a stable `_tag` in its body, and a client that gets no body, as from a proxy, falls back on
 * the status alone. */
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
