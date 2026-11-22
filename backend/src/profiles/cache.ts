import { Clock, Effect, Option, Schema } from "effect";

import { PlayerNotFound, ProfileNotFound, UpstreamUnavailable } from "./model.ts";
import { ProfileStore, type WriteRefused } from "./store.ts";
import { Upstream, UpstreamDown } from "./upstream.ts";
import { decodeAnswer, decodeCatalog, decodeFound, NotFoundBody, unmappedPaths } from "./wire.ts";
import type { Found, Progression } from "./wire.ts";

export const FRESH_MS = 2 * 60 * 1000;
export const KEEP_SECONDS = 24 * 60 * 60;
/** @keep Renewed well inside KEEP_SECONDS, or the name expires while the copies it leads to are kept, and dragsim.co
 * being down then finds none. Renewing on every lookup would write one key twice in a second. */
export const NAME_RENEW_MS = 60 * 60 * 1000;
const NAME_KEEP_SECONDS = KEEP_SECONDS + NAME_RENEW_MS / 1000;
export const MISSING_SECONDS = 60;
export const CATALOG_FRESH_MS = 60 * 60 * 1000;
export const CATALOG_RETRY_MS = 60 * 1000;

// @keep Each KV key has one purpose, and a client chooses none of its parts but the name, which is lowercase and validated.
const nameKey = (lower: string) => `name:${lower}`;
const missingKey = (lower: string) => `missing:${lower}`;
const profileKey = (uuid: string, profileId: string | undefined) => `profile:${uuid}:${profileId ?? "selected"}`;
const CATALOG_KEY = "catalog";

// @keep The body is read again each time, so a change of ours never trusts a copy.
const Entry = Schema.Struct({ fetchedAt: Schema.Number, body: Schema.Unknown });
const decodeEntry = Schema.decodeUnknownOption(Entry);

const Name = Schema.Struct({ uuid: Schema.String, writtenAt: Schema.Number });
const decodeName = Schema.decodeUnknownOption(Name);

export interface Lookup {
  readonly fetchedAt: number;
  readonly stale: boolean;
  readonly found: Found;
}

const readEntry = Effect.fn(function* (store: ProfileStore["Service"], key: string) {
  const entry = decodeEntry(yield* store.get(key));
  return Option.flatMap(entry, ({ fetchedAt, body }) =>
    Option.map(decodeFound(body), (found) => ({ fetchedAt, found })),
  );
});

/** @keep The player hid the items, so no copy of them is made, in a snapshot or here. */
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
      wardrobe: profile.wardrobe?.equipped === undefined ? undefined : { equipped: profile.wardrobe.equipped },
      accessory_bag: undefined,
      storage: pages,
    },
  };
};

const MISSING_PLAYER = /^No DragSim player/u;

const fetchPlayer = Effect.fn(function* (name: string, profileId: string | undefined) {
  const { status, body } = yield* (yield* Upstream).get(
    `/api/player/${name}?mode=main${profileId === undefined ? "" : `&profile=${profileId}`}`,
  );
  if (status === 404) {
    const message = Option.match(Schema.decodeUnknownOption(NotFoundBody)(body), {
      onNone: () => "",
      onSome: (decoded) => decoded.error,
    });
    // @keep Asked for a profile, a 404 says either. Only the server's own words for a missing player mean that.
    if (MISSING_PLAYER.test(message)) {
      return yield* new PlayerNotFound();
    }
    if (profileId !== undefined) {
      return yield* new ProfileNotFound();
    }
  }
  if (status !== 200) {
    yield* Effect.logWarning(`dragsim.co answered ${String(status)} for a player`);
    return yield* new UpstreamDown();
  }
  // @keep An answer we cannot read is dragsim.co's change, not the client's mistake: it counts as down, so an older copy serves.
  const answer = yield* decodeAnswer(body).pipe(
    Effect.tapError((error) => Effect.logError(`dragsim.co's player answer did not decode: ${error.message}`)),
    Effect.mapError(() => new UpstreamDown()),
  );
  const unmapped = unmappedPaths(body);
  if (unmapped.length > 0) {
    yield* Effect.logWarning(`dragsim.co's player answer has fields the backend does not map: ${unmapped.join(", ")}`);
  }
  if (answer.profile === undefined || answer.profile === null) {
    return yield* new ProfileNotFound();
  }
  return { found: hide({ ...answer, profile: answer.profile }) };
});

const keep = (write: Effect.Effect<void, WriteRefused>) =>
  write.pipe(Effect.catchTag("WriteRefused", () => Effect.logWarning("The store refused a copy of a lookup")));

/** @keep When dragsim.co cannot be reached the last copy answers as stale, however old: a day-old profile beats none. */
export const lookup = Effect.fn(function* (name: string, profileId: string | undefined) {
  const store = yield* ProfileStore;
  const lower = name.toLowerCase();
  const now = yield* Clock.currentTimeMillis;
  const named = decodeName(yield* store.get(nameKey(lower)));
  const cached = Option.isSome(named)
    ? yield* readEntry(store, profileKey(named.value.uuid, profileId))
    : Option.none();
  if (Option.isSome(cached) && now - cached.value.fetchedAt < FRESH_MS) {
    return { ...cached.value, stale: false };
  }
  // @keep Checked even for a name once known, which dragsim.co answers 404 for after a rename or a ban.
  if ((yield* store.get(missingKey(lower))) !== null) {
    return yield* new PlayerNotFound();
  }
  return yield* fetchPlayer(name, profileId).pipe(
    Effect.tap(({ found }) =>
      Effect.all([
        Option.isSome(named) && named.value.uuid === found.player.uuid && now - named.value.writtenAt < NAME_RENEW_MS
          ? Effect.void
          : keep(store.put(nameKey(lower), { uuid: found.player.uuid, writtenAt: now }, NAME_KEEP_SECONDS)),
        keep(store.put(profileKey(found.player.uuid, profileId), { fetchedAt: now, body: found }, KEEP_SECONDS)),
      ]),
    ),
    Effect.map(({ found }) => ({ fetchedAt: now, stale: false, found })),
    Effect.catchTag("UpstreamDown", () =>
      Option.match(cached, {
        onNone: () => Effect.fail(new UpstreamUnavailable()),
        onSome: (copy) => Effect.succeed({ ...copy, stale: true }),
      }),
    ),
    Effect.tapErrorTag("PlayerNotFound", () => keep(store.put(missingKey(lower), 1, MISSING_SECONDS))),
  );
});

export interface Catalog {
  readonly fetchedAt: number;
  readonly raw: unknown;
  readonly progression: Progression;
}

// @keep One parse of 2.3 MB per Worker isolate and hour.
const parsed = new WeakMap<object, Catalog>();
const retryAt = new WeakMap<object, number>();

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

/** @keep A catalog that cannot be fetched is replaced by an older copy, with a minute before the next try, because the
 * tables change rarely. Without any copy the lookup is unavailable. */
export const catalog = Effect.gen(function* () {
  const store = yield* ProfileStore;
  const now = yield* Clock.currentTimeMillis;
  const own = parsed.get(store);
  if (own !== undefined && now - own.fetchedAt < CATALOG_FRESH_MS) {
    return own;
  }
  if (own !== undefined && now < (retryAt.get(store) ?? 0)) {
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
    Effect.tap((fetched) => keep(store.put(CATALOG_KEY, { fetchedAt: now, body: fetched.raw }, KEEP_SECONDS))),
    Effect.tap((fetched) => Effect.sync(() => parsed.set(store, fetched))),
    Effect.catchTag("UpstreamDown", () => {
      if (newest === undefined) {
        return Effect.fail(new UpstreamUnavailable());
      }
      parsed.set(store, newest);
      retryAt.set(store, now + CATALOG_RETRY_MS);
      return Effect.succeed(newest);
    }),
  );
});
