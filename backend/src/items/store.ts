import { Context, Effect } from "effect";

import type { ItemAppearance, ItemSighting } from "./model.ts";

/**
 * What the players who agree on an item saw, and so what the renderer may trust. Each needs enough different players,
 * because one client can send anything. `glint` is null when too few players saw the item.
 */
export interface ItemHints {
  readonly glint: "always" | "never" | "mixed" | null;
  /** Skull textures by `skinId`, `""` for the item's own, each on `textures.minecraft.net`. */
  readonly textures: ReadonlyMap<string, string>;
}

/** What one player saw of one item in one sighting, cut from an `ItemSighting` by `items.ts`. */
export interface Observation {
  readonly entry: string;
  /** The item's `skinId`, `""` for none. */
  readonly skinId: string;
  /** The skull texture, already known to be on `textures.minecraft.net`. */
  readonly texture: string | null;
  readonly glint: boolean;
  /** Kept as the agent's example, or null when it is too big to keep. */
  readonly sample: ItemSighting | null;
}

export interface ItemLimits {
  /** At most this many (entry, player) rows in all, and as many (entry, player, skin) rows. */
  readonly maxRows: number;
  /** At most this many (entry, player) rows for one player, and as many (entry, player, skin) rows. */
  readonly maxPlayerRows: number;
  /** At most this many samples for one entry, one per player. */
  readonly maxSamples: number;
}

/** How many different players saw an entry. A player who saw both a glint and none counts in `glintPlayers` only. */
export interface EntryTally {
  readonly entry: string;
  readonly players: number;
  /** Players who saw it glint at least once. */
  readonly glintPlayers: number;
  /** Players who saw it glint every time. */
  readonly alwaysGlintPlayers: number;
}

/** How many different players saw this texture on this skin of an entry. */
export interface TextureTally {
  readonly entry: string;
  readonly skinId: string;
  readonly texture: string;
  readonly players: number;
}

/** Players who must agree before the renderer is told. */
export const MIN_PLAYERS = 2;

/** What one request says of one entry, every observation of it folded into one. */
export interface Folded {
  readonly entry: string;
  readonly glint: boolean;
  readonly plain: boolean;
  /** The first sample that fits, or null. */
  readonly sample: ItemSighting | null;
  /** The skull texture by `skinId`, the last one sent for each. */
  readonly textures: ReadonlyMap<string, string>;
}

/** One entry's rows from one request, so a store writes each row once however often it was sent. */
export const fold = (observations: readonly Observation[]): readonly Folded[] => {
  const byEntry = new Map<
    string,
    { glint: boolean; plain: boolean; sample: ItemSighting | null; textures: Map<string, string> }
  >();
  for (const { entry, skinId, texture, glint, sample } of observations) {
    const folded = byEntry.get(entry) ?? {
      glint: false,
      plain: false,
      sample: null,
      textures: new Map<string, string>(),
    };
    folded.glint ||= glint;
    folded.plain ||= !glint;
    folded.sample ??= sample;
    if (texture !== null) {
      folded.textures.set(skinId, texture);
    }
    byEntry.set(entry, folded);
  }
  return [...byEntry].map(([entry, folded]) => ({ entry, ...folded }));
};

/** How many new rows a player may still add, given the rows they and everyone have. */
export const roomFor = (limits: ItemLimits, playerRows: number, totalRows: number): number =>
  Math.max(0, Math.min(limits.maxPlayerRows - playerRows, limits.maxRows - totalRows));

/** The items a store keeps: every one it already has a row for, and the first `room` of the others. */
export const admit = <T>(items: readonly T[], hasRow: (item: T) => boolean, room: number): T[] => {
  let left = room;
  return items.filter((item) => hasRow(item) || left-- > 0);
};

/** One page of entries from a sorted list that holds one more than `limit` when there is a next page. */
export const pageOf = (entries: readonly string[], limit: number) => ({
  entries: entries.slice(0, limit),
  next: entries.length > limit ? (entries[limit - 1] ?? null) : null,
});

/** Which entries an agent asks about: one with its samples, or a page of all of them without. */
export interface AppearanceQuery {
  readonly entry?: string | undefined;
  /** The `next` of the page before, so a page starts after it. */
  readonly after?: string | undefined;
  readonly limit: number;
}

/** The one place the threshold is applied, so the in-memory store and D1 cannot disagree on it. */
export const hintsFrom = (
  entries: readonly string[],
  tallies: readonly EntryTally[],
  textures: readonly TextureTally[],
): ReadonlyMap<string, ItemHints> => {
  const wanted = new Set(entries);
  const hints = new Map<string, ItemHints>();
  for (const tally of tallies) {
    if (!wanted.has(tally.entry)) {
      continue;
    }
    const agreed = new Map<string, TextureTally>();
    for (const seen of textures) {
      const best = agreed.get(seen.skinId);
      // Two textures for one skin cannot both be right: the one more players saw wins, and the first of equals.
      if (
        seen.entry === tally.entry &&
        seen.players >= MIN_PLAYERS &&
        (best === undefined ||
          seen.players > best.players ||
          (seen.players === best.players && seen.texture < best.texture))
      ) {
        agreed.set(seen.skinId, seen);
      }
    }
    let glint: ItemHints["glint"] = null;
    if (tally.players >= MIN_PLAYERS) {
      glint = tally.alwaysGlintPlayers === tally.players ? "always" : tally.glintPlayers === 0 ? "never" : "mixed";
    }
    hints.set(tally.entry, { glint, textures: new Map([...agreed].map(([skin, seen]) => [skin, seen.texture])) });
  }
  return hints;
};

/** The sightings items have. A rule about them lives in `items.ts`, and this only reads and writes. */
export class ItemStore extends Context.Service<
  ItemStore,
  {
    /**
     * Remembers what one player saw, once per player however often they send it. A row for a new entry is kept only
     * while the player has fewer than `limits.maxPlayerRows` and everyone fewer than `limits.maxRows`, and an entry
     * whose row was refused keeps no texture and no sample.
     */
    readonly record: (
      userId: string,
      modVersion: string,
      observations: readonly Observation[],
      limits: ItemLimits,
    ) => Effect.Effect<void>;
    /**
     * What players saw, for an agent, with no user id. One entry comes with its samples. Without one, a page of
     * entries in order comes without, so a reply is never bigger than `limit` entries and the samples of one.
     */
    readonly appearances: (query: AppearanceQuery) => Effect.Effect<{
      readonly items: readonly ItemAppearance[];
      /** The `after` for the next page, or null at the end. */
      readonly next: string | null;
    }>;
    /** What enough players agree on, for the entries asked about. An entry nobody saw is left out. */
    readonly hints: (entries: readonly string[]) => Effect.Effect<ReadonlyMap<string, ItemHints>>;
  }
>()("dsmod/items/ItemStore") {}

/** Builds the agent's view from the tallies and samples any store keeps. */
export const appearancesFrom = (
  tallies: readonly EntryTally[],
  textures: readonly TextureTally[],
  samples: ReadonlyMap<string, readonly ItemSighting[]>,
): readonly ItemAppearance[] =>
  tallies.map(({ entry, players, glintPlayers }) => ({
    entry,
    players,
    glintPlayers,
    textures: textures
      .filter((seen) => seen.entry === entry)
      .map(({ skinId, texture, players: seen }) => ({ skinId, texture, players: seen })),
    samples: samples.get(entry) ?? [],
  }));

const rowsOf = (rows: ReadonlyMap<string, { readonly userId: string }>, userId: string) =>
  [...rows.values()].filter((row) => row.userId === userId).length;

/** A store that forgets everything when the process ends, for the tests. */
export const memoryItemStore = (): ItemStore["Service"] => {
  interface Seen {
    readonly entry: string;
    readonly userId: string;
    glint: boolean;
    plain: boolean;
  }
  const seen = new Map<string, Seen>();
  const textures = new Map<string, TextureTally & { readonly userId: string }>();
  const samples = new Map<string, Map<string, ItemSighting>>();

  const tallies = (): EntryTally[] => {
    const byEntry = new Map<string, { players: number; glintPlayers: number; alwaysGlintPlayers: number }>();
    for (const row of seen.values()) {
      const tally = byEntry.get(row.entry) ?? { players: 0, glintPlayers: 0, alwaysGlintPlayers: 0 };
      tally.players += 1;
      tally.glintPlayers += row.glint ? 1 : 0;
      tally.alwaysGlintPlayers += row.glint && !row.plain ? 1 : 0;
      byEntry.set(row.entry, tally);
    }
    return [...byEntry].map(([entry, tally]) => ({ entry, ...tally }));
  };

  const textureTallies = (): TextureTally[] => {
    const byKey = new Map<string, TextureTally>();
    for (const row of textures.values()) {
      const key = `${row.entry}\n${row.skinId}\n${row.texture}`;
      byKey.set(key, { ...row, players: (byKey.get(key)?.players ?? 0) + 1 });
    }
    return [...byKey.values()].map(({ entry, skinId, texture, players }) => ({ entry, skinId, texture, players }));
  };

  return {
    record: (userId, _modVersion, observations, limits) =>
      Effect.sync(() => {
        const rowKey = (entry: string) => `${entry}\n${userId}`;
        const kept = admit(
          fold(observations),
          ({ entry }) => seen.has(rowKey(entry)),
          roomFor(limits, rowsOf(seen, userId), seen.size),
        );
        for (const { entry, glint, plain } of kept) {
          const row = seen.get(rowKey(entry)) ?? { entry, userId, glint: false, plain: false };
          row.glint ||= glint;
          row.plain ||= plain;
          seen.set(rowKey(entry), row);
        }
        const wanted = kept.flatMap(({ entry, textures: byskin }) =>
          [...byskin].map(([skinId, texture]) => ({ key: `${rowKey(entry)}\n${skinId}`, entry, skinId, texture })),
        );
        for (const { key, entry, skinId, texture } of admit(
          wanted,
          (row) => textures.has(row.key),
          roomFor(limits, rowsOf(textures, userId), textures.size),
        )) {
          textures.set(key, { entry, skinId, texture, players: 1, userId });
        }
        for (const { entry, sample } of kept) {
          const byPlayer = samples.get(entry) ?? new Map<string, ItemSighting>();
          if (sample !== null && !byPlayer.has(userId) && byPlayer.size < limits.maxSamples) {
            byPlayer.set(userId, sample);
            samples.set(entry, byPlayer);
          }
        }
      }),
    appearances: ({ entry, after, limit }) =>
      Effect.sync(() => {
        const all = tallies().toSorted((a, b) => (a.entry < b.entry ? -1 : 1));
        if (entry !== undefined) {
          const only = all.filter((tally) => tally.entry === entry);
          const kept = new Map([[entry, [...(samples.get(entry)?.values() ?? [])]]]);
          return { items: appearancesFrom(only, textureTallies(), kept), next: null };
        }
        const { entries, next } = pageOf(
          all.map((tally) => tally.entry).filter((name) => after === undefined || name > after),
          limit,
        );
        const wanted = new Set(entries);
        return {
          items: appearancesFrom(
            all.filter((tally) => wanted.has(tally.entry)),
            textureTallies(),
            new Map(),
          ),
          next,
        };
      }),
    hints: (entries) => Effect.sync(() => hintsFrom(entries, tallies(), textureTallies())),
  };
};
