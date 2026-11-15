import { Schema } from "effect";

import { DrawnItem, Line } from "../items/model.ts";

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
  topKills: Schema.Array(Schema.Struct({ id: Schema.String, name: Schema.String, kills: Schema.Int })),
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
