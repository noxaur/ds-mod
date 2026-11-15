import { Effect, Schema } from "effect";
import { and, asc, db, eq, inArray, sql } from "void/db";
import { kv } from "void/kv";
import { storage } from "void/storage";

import { itemSamples, itemSeen, itemTextures, requests, skips, submissions, unknowns } from "../db/schema.ts";
import { RecordingRequest, RequestSpec } from "../src/contrib/model.ts";
import type { Store } from "../src/contrib/store.ts";
import { ItemSighting } from "../src/items/model.ts";
import { appearancesFrom, type EntryTally, hintsFrom, type ItemStore, type TextureTally } from "../src/items/store.ts";
import type { ProfileStore } from "../src/profiles/store.ts";
import { type Upstream, UpstreamDown } from "../src/profiles/upstream.ts";

const decodeSpec = Schema.decodeUnknownSync(RequestSpec);
const decodeSighting = Schema.decodeUnknownSync(ItemSighting);
const decodeStatus = Schema.decodeUnknownSync(RecordingRequest.fields.status);
// A database or storage failure is a defect: no client can do anything about it. makeApp logs it and answers 500.
const run = <A>(query: () => PromiseLike<A>) => Effect.orDie(Effect.tryPromise(query));

const toRequest = (row: typeof requests.$inferSelect) => ({
  ...decodeSpec(row.spec),
  id: row.id,
  status: decodeStatus(row.status),
  createdAt: row.createdAt,
});

/** The store on Cloudflare: rows in D1 and capture files in R2. It outlives a move off Void as the only file to rewrite. */
export const contribStore: Store["Service"] = {
  insertRequest: ({ id, status, createdAt, ...spec }) =>
    run(() => db.insert(requests).values({ id, status, createdAt, spec })).pipe(Effect.asVoid),
  requests: run(() => db.select().from(requests).orderBy(asc(requests.createdAt))).pipe(
    Effect.map((rows) => rows.map(toRequest)),
  ),
  request: (id) =>
    run(() => db.select().from(requests).where(eq(requests.id, id))).pipe(
      Effect.map((rows) => (rows[0] === undefined ? null : toRequest(rows[0]))),
    ),
  fill: (id) =>
    run(() =>
      db
        .update(requests)
        .set({ status: "filled" })
        .where(and(eq(requests.id, id), eq(requests.status, "open"))),
    ).pipe(Effect.asVoid),
  close: (id) =>
    run(() => db.update(requests).set({ status: "closed" }).where(eq(requests.id, id))).pipe(Effect.asVoid),
  insertSkip: (requestId, userId) =>
    run(() => db.insert(skips).values({ requestId, userId }).onConflictDoNothing()).pipe(Effect.asVoid),
  skips: run(() => db.select().from(skips)),
  doneBy: (userId) =>
    run(async () => [
      ...(await db.select({ id: skips.requestId }).from(skips).where(eq(skips.userId, userId))),
      ...(await db.select({ id: submissions.requestId }).from(submissions).where(eq(submissions.userId, userId))),
    ]).pipe(Effect.map((rows) => rows.map((row) => row.id))),
  insertSubmission: (row, capture) =>
    run(async () => {
      // The file first, so a row always has one: a run that counts towards filling a request is a run an agent can
      // read. When the row is refused or never written, the file is left with no row, which nothing reads.
      await storage.put(`captures/${row.id}`, capture);
      // The row's unique index is what stops one player's two uploads at once from both being kept.
      const kept = await db.insert(submissions).values(row).onConflictDoNothing().returning({ id: submissions.id });
      if (kept.length === 0) {
        await storage.delete(`captures/${row.id}`);
      }
      return kept.length > 0;
    }),
  submissions: run(() => db.select().from(submissions).orderBy(asc(submissions.createdAt), asc(submissions.id))),
  submissionsOf: (requestId) =>
    run(() =>
      db
        .select()
        .from(submissions)
        .where(eq(submissions.requestId, requestId))
        .orderBy(asc(submissions.createdAt), asc(submissions.id)),
    ),
  capture: (id) =>
    run(async () => {
      const object = await storage.get(`captures/${id}`);
      return object === null ? null : new Uint8Array(await object.arrayBuffer());
    }),
  countUnknown: (unknown, maxRows) =>
    run(async () => {
      const counted = await db
        .update(unknowns)
        .set({ count: sql`${unknowns.count} + 1` })
        .where(and(eq(unknowns.kind, unknown.kind), eq(unknowns.signature, unknown.signature)))
        .returning({ kind: unknowns.kind });
      if (counted.length > 0) {
        return;
      }
      const [total] = await db.select({ rows: sql<number>`count(*)` }).from(unknowns);
      if ((total?.rows ?? 0) < maxRows) {
        await db
          .insert(unknowns)
          .values({ ...unknown, count: 1 })
          .onConflictDoUpdate({
            target: [unknowns.kind, unknowns.signature],
            set: { count: sql`${unknowns.count} + 1` },
          });
      }
    }),
  unknowns: run(() => db.select().from(unknowns)),
};

/** Copies of dragsim.co's answers in KV, which drops each when its time is up. */
export const profileStore: ProfileStore["Service"] = {
  get: (key) => run(() => kv.get(key)),
  put: (key, value, ttlSeconds) => run(() => kv.put(key, value, { ttl: ttlSeconds })),
};

/** dragsim.co itself. A network failure, or an answer that is not JSON, is dragsim.co being down. */
export const upstream: Upstream["Service"] = {
  get: (path) =>
    Effect.tryPromise({
      try: async () => {
        const response = await fetch(`https://dragsim.co${path}`, { signal: AbortSignal.timeout(10_000) });
        return { status: response.status, body: await response.json() };
      },
      catch: () => new UpstreamDown(),
    }),
};

const tallies = (entries?: readonly string[]) =>
  db
    .select({
      entry: itemSeen.entry,
      players: sql<number>`count(*)`,
      glintPlayers: sql<number>`sum(${itemSeen.glint})`,
      alwaysGlintPlayers: sql<number>`sum(${itemSeen.glint} * (1 - ${itemSeen.plain}))`,
    })
    .from(itemSeen)
    .where(entries === undefined ? undefined : inArray(itemSeen.entry, [...entries]))
    .groupBy(itemSeen.entry);

const textureTallies = (entries?: readonly string[]) =>
  db
    .select({
      entry: itemTextures.entry,
      skinId: itemTextures.skinId,
      texture: itemTextures.texture,
      players: sql<number>`count(*)`,
    })
    .from(itemTextures)
    .where(entries === undefined ? undefined : inArray(itemTextures.entry, [...entries]))
    .groupBy(itemTextures.entry, itemTextures.skinId, itemTextures.texture);

const rowsIn = async (table: typeof itemSeen | typeof itemTextures) =>
  (await db.select({ rows: sql<number>`count(*)` }).from(table))[0]?.rows ?? 0;

// D1 takes at most 100 bound values in a statement.
const CHUNK = 90;

/** The sightings in D1. Each statement is one the database runs alone, and the rules are in `items.ts`. */
export const itemStore: ItemStore["Service"] = {
  record: (userId, modVersion, observations, limits) =>
    run(async () => {
      let seen = await rowsIn(itemSeen);
      let textures = await rowsIn(itemTextures);
      for (const { entry, skinId, texture, glint, sample } of observations) {
        const flags = { glint: glint ? 1 : 0, plain: glint ? 0 : 1 };
        const updated = await db
          .update(itemSeen)
          .set({
            glint: sql`max(${itemSeen.glint}, ${flags.glint})`,
            plain: sql`max(${itemSeen.plain}, ${flags.plain})`,
            modVersion,
          })
          .where(and(eq(itemSeen.entry, entry), eq(itemSeen.userId, userId)))
          .returning({ entry: itemSeen.entry });
        if (updated.length === 0) {
          if (seen >= limits.maxRows) {
            continue;
          }
          await db
            .insert(itemSeen)
            .values({ entry, userId, ...flags, modVersion })
            .onConflictDoNothing();
          seen += 1;
        }
        if (texture !== null) {
          const changed = await db
            .update(itemTextures)
            .set({ texture })
            .where(and(eq(itemTextures.entry, entry), eq(itemTextures.userId, userId), eq(itemTextures.skinId, skinId)))
            .returning({ entry: itemTextures.entry });
          if (changed.length === 0 && textures < limits.maxRows) {
            await db.insert(itemTextures).values({ entry, userId, skinId, texture }).onConflictDoNothing();
            textures += 1;
          }
        }
        if (sample !== null) {
          const [kept] = await db
            .select({ rows: sql<number>`count(*)` })
            .from(itemSamples)
            .where(eq(itemSamples.entry, entry));
          if ((kept?.rows ?? 0) < limits.maxSamples) {
            await db.insert(itemSamples).values({ entry, userId, sample }).onConflictDoNothing();
          }
        }
      }
    }),
  appearances: run(async () => {
    const samples = new Map<string, ItemSighting[]>();
    for (const row of await db.select({ entry: itemSamples.entry, sample: itemSamples.sample }).from(itemSamples)) {
      samples.set(row.entry, [...(samples.get(row.entry) ?? []), decodeSighting(row.sample)]);
    }
    return appearancesFrom(await tallies(), await textureTallies(), samples);
  }),
  hints: (entries) =>
    run(async () => {
      const found: EntryTally[] = [];
      const textures: TextureTally[] = [];
      for (let from = 0; from < entries.length; from += CHUNK) {
        const chunk = entries.slice(from, from + CHUNK);
        found.push(...(await tallies(chunk)));
        textures.push(...(await textureTallies(chunk)));
      }
      return hintsFrom(entries, found, textures);
    }),
};
