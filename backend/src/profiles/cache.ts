import { Clock, Effect, Option, Schema } from "effect";

import { newId } from "../ids.ts";
import { PlayerNotFound, ProfileId, ProfileNotFound, UpstreamUnavailable } from "./model.ts";
import { ProfileStore, type WriteRefused } from "./store.ts";
import { Upstream, UpstreamDown } from "./upstream.ts";
import { decodeAnswer, decodeCatalog, decodeFound, NotFoundBody, Sections, sectionsOf, unmappedPaths } from "./wire.ts";
import type { Found, Progression } from "./wire.ts";

export const FRESH_MS = 2 * 60 * 1000;
export const KEEP_SECONDS = 24 * 60 * 60;
export const NAME_RENEW_MS = 60 * 60 * 1000;
const NAME_KEEP_SECONDS = KEEP_SECONDS + NAME_RENEW_MS / 1000;
export const MISSING_SECONDS = 60;
export const CATALOG_FRESH_MS = 60 * 60 * 1000;
export const CATALOG_RETRY_MS = 60 * 1000;
export const SNAPSHOT_SECONDS = 10 * 60;
const SNAPSHOT_KEEP_SECONDS = SNAPSHOT_SECONDS + FRESH_MS / 1000;

// @keep A client chooses the name in a KV key, so it is lowercase and validated.
const nameKey = (lower: string) => `name:${lower}`;
const missingKey = (lower: string) => `missing:${lower}`;
const profileKey = (uuid: string, profileId: string | undefined) => `profile:${uuid}:${profileId ?? "selected"}`;
const snapshotKey = (id: string) => `snapshot:${id}`;
const CATALOG_KEY = "catalog";

const Entry = Schema.Struct({ fetchedAt: Schema.Number, body: Schema.Unknown });
const decodeEntry = Schema.decodeUnknownOption(Entry);

// @keep A copy kept before snapshots were reused has no snapshot.
const ProfileEntry = Schema.Struct({ ...Entry.fields, snapshot: Schema.optional(Schema.String) });
const decodeProfileEntry = Schema.decodeUnknownOption(ProfileEntry);

const Snapshot = Schema.Struct({ hidden: Schema.Boolean, sections: Sections });
const decodeSnapshot = Schema.decodeUnknownOption(Snapshot);

// @keep A snapshot id comes from the client and is a KV key, so only the shape this backend makes reaches the store.
const isSnapshotId = Schema.is(ProfileId);

const Name = Schema.Struct({ uuid: Schema.String, writtenAt: Schema.Number });
const decodeName = Schema.decodeUnknownOption(Name);

export interface Lookup {
  readonly fetchedAt: number;
  readonly stale: boolean;
  readonly found: Found;
  readonly snapshot: string;
}

const readEntry = Effect.fn(function* (store: ProfileStore["Service"], key: string) {
  const entry = decodeProfileEntry(yield* store.get(key));
  return Option.flatMap(entry, ({ fetchedAt, body, snapshot }) =>
    Option.map(decodeFound(body), (found) => ({ fetchedAt, found, snapshot })),
  );
});

const putSnapshot = Effect.fn(function* (store: ProfileStore["Service"], found: Found) {
  const id = yield* newId;
  const kept = { hidden: found.profile.inventory_hidden === true, sections: sectionsOf(found.profile) };
  yield* Effect.orDie(store.put(snapshotKey(id), kept, SNAPSHOT_KEEP_SECONDS));
  return id;
});

export const readSnapshot = Effect.fn(function* (id: string) {
  return isSnapshotId(id) ? decodeSnapshot(yield* (yield* ProfileStore).get(snapshotKey(id))) : Option.none();
});

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
    // @keep dragsim.co answers 404 for a missing player and a missing profile alike; only its words for a missing player tell them apart.
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

export const lookup = Effect.fn(function* (name: string, profileId: string | undefined) {
  const store = yield* ProfileStore;
  const lower = name.toLowerCase();
  const now = yield* Clock.currentTimeMillis;
  const named = decodeName(yield* store.get(nameKey(lower)));
  const cached = Option.isSome(named)
    ? yield* readEntry(store, profileKey(named.value.uuid, profileId))
    : Option.none();
  if (Option.isSome(cached) && now - cached.value.fetchedAt < FRESH_MS) {
    const { snapshot = yield* putSnapshot(store, cached.value.found) } = cached.value;
    return { ...cached.value, stale: false, snapshot };
  }
  // @keep dragsim.co answers 404 for a name once known after a rename or a ban.
  if ((yield* store.get(missingKey(lower))) !== null) {
    return yield* new PlayerNotFound();
  }
  return yield* fetchPlayer(name, profileId).pipe(
    Effect.flatMap(({ found }) =>
      Effect.gen(function* () {
        const snapshot = yield* putSnapshot(store, found);
        yield* Effect.all([
          Option.isSome(named) && named.value.uuid === found.player.uuid && now - named.value.writtenAt < NAME_RENEW_MS
            ? Effect.void
            : keep(store.put(nameKey(lower), { uuid: found.player.uuid, writtenAt: now }, NAME_KEEP_SECONDS)),
          keep(
            store.put(
              profileKey(found.player.uuid, profileId),
              { fetchedAt: now, body: found, snapshot },
              KEEP_SECONDS,
            ),
          ),
        ]);
        return { fetchedAt: now, stale: false, found, snapshot };
      }),
    ),
    Effect.catchTag("UpstreamDown", () =>
      Option.match(cached, {
        onNone: () => Effect.fail(new UpstreamUnavailable()),
        onSome: (copy) =>
          Effect.map(putSnapshot(store, copy.found), (snapshot) => ({ ...copy, stale: true, snapshot })),
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
