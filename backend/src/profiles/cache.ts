import { Clock, Effect, Option, Schema } from "effect";

import { PlayerNotFound, ProfileNotFound, UpstreamUnavailable } from "./model.ts";
import { ProfileStore } from "./store.ts";
import { Upstream, UpstreamDown } from "./upstream.ts";
import { decodeAnswer, decodeCatalog, decodeFound, NotFoundBody } from "./wire.ts";
import type { Found, Progression } from "./wire.ts";

/** How long a copy of a player's answer is served without asking dragsim.co again. A player's profile changes slowly. */
export const FRESH_MS = 2 * 60 * 1000;
/** How long a copy is kept as the answer for when dragsim.co is down, and how long a name is remembered. */
export const KEEP_SECONDS = 24 * 60 * 60;
/** How long a name dragsim.co does not know is remembered, so a mistyped name asked again does not reach it. */
export const MISSING_SECONDS = 60;
/** How long the catalog is used before it is fetched again. It changes with the server's releases, not by the minute. */
export const CATALOG_FRESH_MS = 60 * 60 * 1000;

// Each KV key has one purpose, and a client chooses none of its parts but the name, which is lowercase and validated.
const nameKey = (lower: string) => `name:${lower}`;
const missingKey = (lower: string) => `missing:${lower}`;
const profileKey = (uuid: string, profileId: string | undefined) => `profile:${uuid}:${profileId ?? "selected"}`;
const CATALOG_KEY = "catalog";

/** A copy of what dragsim.co answered, and when. The body is read again each time, so a change of ours never trusts it. */
const Entry = Schema.Struct({ fetchedAt: Schema.Number, body: Schema.Unknown });
const decodeEntry = Schema.decodeUnknownOption(Entry);

export interface Lookup {
  /** When the backend read it from dragsim.co, in epoch milliseconds. */
  readonly fetchedAt: number;
  readonly stale: boolean;
  readonly found: Found;
}

const readEntry = Effect.fn(function* (store: ProfileStore["Service"], key: string) {
  const entry = decodeEntry(yield* store.get(key));
  // A copy that no longer reads, such as from before a change of ours, is as good as none.
  return Option.flatMap(entry, ({ fetchedAt, body }) =>
    Option.map(decodeFound(body), (found) => ({ fetchedAt, found })),
  );
});

/**
 * What a hidden profile keeps: its numbers, and the names of its storage pages so the containers can be listed. The
 * player hid the items, so no copy of them is made, in a snapshot or here.
 */
const hide = (found: Found): Found => {
  const { profile } = found;
  if (profile.inventory_hidden !== true) {
    return found;
  }
  const pages = Object.fromEntries(Object.keys(profile.storage ?? {}).map((key) => [key, {}]));
  return {
    ...found,
    profile: {
      ...profile,
      inventory: undefined,
      ender_chest: undefined,
      wardrobe: undefined,
      accessory_bag: undefined,
      storage: pages,
    },
  };
};

const MISSING_PLAYER = /^No DragSim player/u;

/** Asks dragsim.co for the player. Anything but its word that the player or profile is missing is it being down. */
const fetchPlayer = Effect.fn(function* (name: string, profileId: string | undefined) {
  const { status, body } = yield* (yield* Upstream).get(
    `/api/player/${name}?mode=main${profileId === undefined ? "" : `&profile=${profileId}`}`,
  );
  if (status === 404) {
    const message = Option.match(Schema.decodeUnknownOption(NotFoundBody)(body), {
      onNone: () => "",
      onSome: (decoded) => decoded.error,
    });
    // Asked for a profile, a 404 says either. Only the server's own words for a missing player mean that.
    return yield* profileId !== undefined && !MISSING_PLAYER.test(message)
      ? new ProfileNotFound()
      : new PlayerNotFound();
  }
  if (status !== 200) {
    yield* Effect.logWarning(`dragsim.co answered ${String(status)} for a player`);
    return yield* new UpstreamDown();
  }
  // An answer we cannot read is dragsim.co's change and not the client's mistake. It is logged and treated as down,
  // so a copy from before the change still serves, and nothing unreadable is ever kept.
  const answer = yield* decodeAnswer(body).pipe(
    Effect.tapError((error) => Effect.logError(`dragsim.co's player answer did not decode: ${error.message}`)),
    Effect.mapError(() => new UpstreamDown()),
  );
  if (answer.profile === undefined || answer.profile === null) {
    return yield* new ProfileNotFound();
  }
  return { found: hide({ ...answer, profile: answer.profile }) };
});

/**
 * A player's answer, from the backend's copy while it is fresh, else from dragsim.co. When dragsim.co cannot be
 * reached the last copy it sent answers as stale, however old, because a profile a day old beats no profile.
 */
export const lookup = Effect.fn(function* (name: string, profileId: string | undefined) {
  const store = yield* ProfileStore;
  const lower = name.toLowerCase();
  const now = yield* Clock.currentTimeMillis;
  const uuid = yield* store.get(nameKey(lower));
  const known = typeof uuid === "string";
  const cached = known ? yield* readEntry(store, profileKey(uuid, profileId)) : Option.none();
  if (Option.isSome(cached) && now - cached.value.fetchedAt < FRESH_MS) {
    return { ...cached.value, stale: false };
  }
  if (!known && (yield* store.get(missingKey(lower))) !== null) {
    return yield* new PlayerNotFound();
  }
  return yield* fetchPlayer(name, profileId).pipe(
    Effect.tap(({ found }) =>
      Effect.all([
        store.put(nameKey(lower), found.player.uuid, KEEP_SECONDS),
        store.put(profileKey(found.player.uuid, profileId), { fetchedAt: now, body: found }, KEEP_SECONDS),
      ]),
    ),
    Effect.map(({ found }) => ({ fetchedAt: now, stale: false, found })),
    Effect.catchTag("UpstreamDown", () =>
      Option.match(cached, {
        onNone: () => Effect.fail(new UpstreamUnavailable()),
        onSome: (copy) => Effect.succeed({ ...copy, stale: true }),
      }),
    ),
    Effect.tapErrorTag("PlayerNotFound", () => store.put(missingKey(lower), 1, MISSING_SECONDS)),
  );
});

/** `catalog.json`, kept raw for the renderer, with the part the backend reads itself. */
export interface Catalog {
  readonly fetchedAt: number;
  readonly raw: unknown;
  readonly progression: Progression;
}

// One parse of 2.3 MB per Worker isolate and hour. The store is the key, so each test's store has a catalog of its own.
const parsed = new WeakMap<object, Catalog>();

const readCatalog = Effect.fn(function* (store: ProfileStore["Service"]) {
  return Option.flatMap(decodeEntry(yield* store.get(CATALOG_KEY)), ({ fetchedAt, body }) =>
    Option.map(decodeCatalog(body), ({ progression }) => ({ fetchedAt, raw: body, progression })),
  );
});

const fetchCatalog = Effect.gen(function* () {
  const { status, body } = yield* (yield* Upstream).get("/data/catalog.json");
  const decoded = status === 200 ? decodeCatalog(body) : Option.none();
  if (Option.isNone(decoded)) {
    yield* Effect.logError(`dragsim.co's catalog was not readable, with status ${String(status)}`);
    return yield* new UpstreamDown();
  }
  return { raw: body, progression: decoded.value.progression };
});

/**
 * The catalog the renderer and the level tables need. It is used while it is an hour old at most, from this isolate's
 * parse or else the one in the store, and fetched again after that. A catalog that cannot be fetched is replaced by an
 * older copy, because the tables change rarely, and without any copy the lookup is unavailable.
 */
export const catalog = Effect.gen(function* () {
  const store = yield* ProfileStore;
  const now = yield* Clock.currentTimeMillis;
  const own = parsed.get(store);
  if (own !== undefined && now - own.fetchedAt < CATALOG_FRESH_MS) {
    return own;
  }
  const shared = Option.getOrUndefined(yield* readCatalog(store));
  const newest = shared !== undefined && (own === undefined || shared.fetchedAt > own.fetchedAt) ? shared : own;
  if (newest !== undefined && now - newest.fetchedAt < CATALOG_FRESH_MS) {
    parsed.set(store, newest);
    return newest;
  }
  return yield* fetchCatalog.pipe(
    Effect.map((fetched) => ({ ...fetched, fetchedAt: now })),
    Effect.tap((fetched) => store.put(CATALOG_KEY, { fetchedAt: now, body: fetched.raw }, KEEP_SECONDS)),
    Effect.tap((fetched) => Effect.sync(() => parsed.set(store, fetched))),
    Effect.catchTag("UpstreamDown", () =>
      newest === undefined ? Effect.fail(new UpstreamUnavailable()) : Effect.succeed(newest),
    ),
  );
});
