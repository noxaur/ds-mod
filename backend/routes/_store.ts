import { Effect, Schema } from "effect";
import { and, asc, db, eq, gt, inArray, sql } from "void/db";
import { kv } from "void/kv";
import { storage } from "void/storage";

import { itemSamples, itemSeen, itemTextures, requests, skips, submissions, unknowns } from "../db/schema.ts";
import { RecordingRequest, RequestSpec } from "../src/contrib/model.ts";
import type { Store } from "../src/contrib/store.ts";
import { ItemSighting } from "../src/items/model.ts";
import {
  admit,
  appearancesFrom,
  type EntryTally,
  fold,
  hintsFrom,
  type ItemStore,
  pageOf,
  roomFor,
  type TextureTally,
} from "../src/items/store.ts";
import { type ProfileStore, WriteRefused } from "../src/profiles/store.ts";
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
  put: (key, value, ttlSeconds) =>
    Effect.tryPromise({ try: () => kv.put(key, value, { ttl: ttlSeconds }), catch: () => new WriteRefused() }),
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

// D1 takes at most 100 bound values in a statement.
const CHUNK = 90;

/** Tallies for any number of entries, in the statements D1's bound-value limit allows. */
const talliesOf = async (entries: readonly string[]) => {
  const found: EntryTally[] = [];
  const textures: TextureTally[] = [];
  for (let from = 0; from < entries.length; from += CHUNK) {
    const chunk = entries.slice(from, from + CHUNK);
    found.push(...(await tallies(chunk)));
    textures.push(...(await textureTallies(chunk)));
  }
  return { found, textures };
};

/** How many rows a table has, counted to `cap` and no further, so a full table is not scanned for each request. */
const cappedRows = (table: typeof itemSeen | typeof itemTextures, cap: number) =>
  db.select({ rows: sql<number>`count(*)` }).from(
    db
      .select({ one: sql`1` })
      .from(table)
      .limit(cap)
      .as("capped"),
  );

type Statements = Parameters<typeof db.batch>[0];

/** Sends statements to D1 as one round trip that succeeds or fails whole. */
const batch = async (statements: Statements[number][]) => {
  const [first, ...rest] = statements;
  if (first !== undefined) {
    await db.batch([first, ...rest]);
  }
};

/** The sightings in D1. The rules are in `items.ts` and `store.ts`, and this reads, then writes in one batch. */
export const itemStore: ItemStore["Service"] = {
  record: (userId, modVersion, observations, limits) =>
    run(async () => {
      const folded = fold(observations);
      if (folded.length === 0) {
        return;
      }
      // At most 50 entries in a request, so one statement holds them all.
      const entries = folded.map(({ entry }) => entry);
      const textured = folded.some(({ textures }) => textures.size > 0);
      const [have, yours, haveTextures, yourTextures, sampled] = await db.batch([
        db
          .select({ entry: itemSeen.entry })
          .from(itemSeen)
          .where(and(eq(itemSeen.userId, userId), inArray(itemSeen.entry, entries))),
        db
          .select({ rows: sql<number>`count(*)` })
          .from(itemSeen)
          .where(eq(itemSeen.userId, userId)),
        db
          .select({ entry: itemTextures.entry, skinId: itemTextures.skinId })
          .from(itemTextures)
          .where(and(eq(itemTextures.userId, userId), inArray(itemTextures.entry, entries))),
        db
          .select({ rows: sql<number>`count(*)` })
          .from(itemTextures)
          .where(eq(itemTextures.userId, userId)),
        db
          .select({ entry: itemSamples.entry, rows: sql<number>`count(*)` })
          .from(itemSamples)
          .where(inArray(itemSamples.entry, entries))
          .groupBy(itemSamples.entry),
      ]);
      const seenKeys = new Set(have.map(({ entry }) => entry));
      const textureKeys = new Set(haveTextures.map(({ entry, skinId }) => `${entry}\n${skinId}`));
      const sampleRows = new Map(sampled.map(({ entry, rows }) => [entry, rows]));
      const wantsRow = folded.some(({ entry }) => !seenKeys.has(entry));
      const wantsTexture =
        textured &&
        folded.some(({ entry, textures }) =>
          [...textures.keys()].some((skin) => !textureKeys.has(`${entry}\n${skin}`)),
        );
      // The table-wide counts only matter when this request would add a row, and they stop at the cap.
      const [everyone, everyoneTextures] = await Promise.all([
        wantsRow ? cappedRows(itemSeen, limits.maxRows) : [{ rows: 0 }],
        wantsTexture ? cappedRows(itemTextures, limits.maxRows) : [{ rows: 0 }],
      ]);
      const kept = admit(
        folded,
        ({ entry }) => seenKeys.has(entry),
        roomFor(limits, yours[0]?.rows ?? 0, everyone[0]?.rows ?? 0),
      );
      const wanted = kept.flatMap(({ entry, textures }) =>
        [...textures].map(([skinId, texture]) => ({ entry, skinId, texture })),
      );
      const keptTextures = admit(
        wanted,
        ({ entry, skinId }) => textureKeys.has(`${entry}\n${skinId}`),
        roomFor(limits, yourTextures[0]?.rows ?? 0, everyoneTextures[0]?.rows ?? 0),
      );
      await batch([
        ...kept.map(({ entry, glint, plain }) =>
          db
            .insert(itemSeen)
            .values({ entry, userId, glint: glint ? 1 : 0, plain: plain ? 1 : 0, modVersion })
            .onConflictDoUpdate({
              target: [itemSeen.entry, itemSeen.userId],
              set: {
                glint: sql`max(${itemSeen.glint}, ${glint ? 1 : 0})`,
                plain: sql`max(${itemSeen.plain}, ${plain ? 1 : 0})`,
                modVersion,
              },
            }),
        ),
        ...keptTextures.map(({ entry, skinId, texture }) =>
          db
            .insert(itemTextures)
            .values({ entry, userId, skinId, texture })
            .onConflictDoUpdate({
              target: [itemTextures.entry, itemTextures.userId, itemTextures.skinId],
              set: { texture },
            }),
        ),
        ...kept.flatMap(({ entry, sample }) =>
          sample !== null && (sampleRows.get(entry) ?? 0) < limits.maxSamples
            ? [db.insert(itemSamples).values({ entry, userId, sample }).onConflictDoNothing()]
            : [],
        ),
      ]);
    }),
  appearances: ({ entry, after, limit }) =>
    run(async () => {
      if (entry !== undefined) {
        const rows = await db
          .select({ sample: itemSamples.sample })
          .from(itemSamples)
          .where(eq(itemSamples.entry, entry));
        const { found, textures } = await talliesOf([entry]);
        const samples = new Map([[entry, rows.map(({ sample }) => decodeSighting(sample))]]);
        return { items: appearancesFrom(found, textures, samples), next: null };
      }
      const names = await db
        .select({ entry: itemSeen.entry })
        .from(itemSeen)
        .where(after === undefined ? undefined : gt(itemSeen.entry, after))
        .groupBy(itemSeen.entry)
        .orderBy(asc(itemSeen.entry))
        .limit(limit + 1);
      const { entries, next } = pageOf(
        names.map(({ entry: name }) => name),
        limit,
      );
      const { found, textures } = await talliesOf(entries);
      return { items: appearancesFrom(found, textures, new Map()), next };
    }),
  hints: (entries) =>
    run(async () => {
      const { found, textures } = await talliesOf(entries);
      return hintsFrom(entries, found, textures);
    }),
};
