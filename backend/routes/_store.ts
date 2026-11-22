import { Effect, Option, Schema } from "effect";
import { and, asc, db, eq, gt, inArray, sql } from "void/db";
import { kv } from "void/kv";
import { storage } from "void/storage";
import { requireRuntimeBinding } from "void/_env";

import { itemSamples, itemSeen, itemTextures, requests, skips, submissions, unknowns } from "../db/schema.ts";
import { RecordingRequest, RequestSpec } from "../src/contrib/model.ts";
import type { Store } from "../src/contrib/store.ts";
import { ItemSighting } from "../src/items/model.ts";
import {
  appearancesFrom,
  type EntryTally,
  fold,
  hintsFrom,
  type ItemStore,
  pageOf,
  type TextureTally,
} from "../src/items/store.ts";
import type { Limiter, Rate } from "../src/limiter.ts";
import { type ProfileStore, WriteRefused } from "../src/profiles/store.ts";
import { type Upstream, UpstreamDown } from "../src/profiles/upstream.ts";

const decodeSpec = Schema.decodeUnknownSync(RequestSpec);
// @keep A sample kept before a bound was tightened no longer decodes, and is left out rather than failing its entry.
const decodeSighting = Schema.decodeUnknownOption(ItemSighting);
const decodeStatus = Schema.decodeUnknownSync(RecordingRequest.fields.status);
const run = <A>(query: () => PromiseLike<A>) => Effect.orDie(Effect.tryPromise(query));

const toRequest = (row: typeof requests.$inferSelect) => ({
  ...decodeSpec(row.spec),
  id: row.id,
  status: decodeStatus(row.status),
  createdAt: row.createdAt,
});

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
  insertSubmission: (row, capture, maxRuns) =>
    run(async () => {
      /** @keep The file first, so a row always has one: a run that counts towards filling a request is a run an agent
       * can read. */
      await storage.put(`captures/${row.id}`, capture);
      // @keep One statement, so neither a second upload nor a close can slip in between the check in contrib.ts and here.
      const kept = await db.all<{ id: string }>(sql`
        INSERT INTO ${submissions} (id, request_id, user_id, mod_version, bytes, created_at)
        SELECT ${row.id}, ${row.requestId}, ${row.userId}, ${row.modVersion}, ${row.bytes}, ${row.createdAt}
        WHERE (SELECT count(*) FROM ${submissions} WHERE request_id = ${row.requestId}) < ${maxRuns}
          AND NOT EXISTS (SELECT 1 FROM ${requests} WHERE id = ${row.requestId} AND status = 'closed')
        ON CONFLICT DO NOTHING
        RETURNING id
      `);
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
    run(() =>
      // @keep One statement, so two new signatures at once cannot both pass the cap.
      db.run(sql`
        INSERT INTO ${unknowns} (kind, signature, sample, count, mod_version, first_seen)
        SELECT ${unknown.kind}, ${unknown.signature}, ${unknown.sample}, 1, ${unknown.modVersion}, ${unknown.firstSeen}
        WHERE EXISTS (
          SELECT 1 FROM ${unknowns} WHERE kind = ${unknown.kind} AND signature = ${unknown.signature}
        ) OR (SELECT count(*) FROM ${unknowns}) < ${maxRows}
        ON CONFLICT(kind, signature) DO UPDATE SET count = count + 1
      `),
    ),
  unknowns: run(() => db.select().from(unknowns)),
};

export const profileStore: ProfileStore["Service"] = {
  get: (key) => run(() => kv.get(key)),
  put: (key, value, ttlSeconds) =>
    Effect.tryPromise({ try: () => kv.put(key, value, { ttl: ttlSeconds }), catch: () => new WriteRefused() }),
};

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

// @keep D1 takes at most 100 bound values in a statement.
const CHUNK = 90;

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

type Statements = Parameters<typeof db.batch>[0];

const batch = async (statements: Statements[number][]) => {
  const [first, ...rest] = statements;
  if (first !== undefined) {
    await db.batch([first, ...rest]);
  }
};

/** @keep The caps are counted inside the insert, so two reports cannot both pass them. */
const seenWrite = (userId: string, modVersion: string, limits: { maxRows: number; maxPlayerRows: number }) => {
  return (entry: string, glint: boolean, plain: boolean) =>
    db
      .insert(itemSeen)
      .select(
        db
          .select({
            entry: sql`${entry}`.as("entry"),
            userId: sql`${userId}`.as("user_id"),
            glint: sql`${glint ? 1 : 0}`.as("glint"),
            plain: sql`${plain ? 1 : 0}`.as("plain"),
            modVersion: sql`${modVersion}`.as("mod_version"),
          })
          .from(sql`(select 1) as one`).where(sql`
            EXISTS (SELECT 1 FROM ${itemSeen} WHERE entry = ${entry} AND user_id = ${userId})
            OR (
              (SELECT count(*) FROM ${itemSeen} WHERE user_id = ${userId}) < ${limits.maxPlayerRows}
              AND (SELECT count(*) FROM ${itemSeen}) < ${limits.maxRows}
            )
          `),
      )
      .onConflictDoUpdate({
        target: [itemSeen.entry, itemSeen.userId],
        set: {
          glint: sql`max(${itemSeen.glint}, excluded.glint)`,
          plain: sql`max(${itemSeen.plain}, excluded.plain)`,
          modVersion: sql`excluded.mod_version`,
        },
      });
};

const textureWrite = (userId: string, limits: { maxRows: number; maxPlayerRows: number }) => {
  return (entry: string, skinId: string, texture: string) =>
    db
      .insert(itemTextures)
      .select(
        db
          .select({
            entry: sql`${entry}`.as("entry"),
            userId: sql`${userId}`.as("user_id"),
            skinId: sql`${skinId}`.as("skin_id"),
            texture: sql`${texture}`.as("texture"),
          })
          .from(sql`(select 1) as one`).where(sql`
            EXISTS (SELECT 1 FROM ${itemSeen} WHERE entry = ${entry} AND user_id = ${userId})
            AND (
              EXISTS (
                SELECT 1 FROM ${itemTextures}
                WHERE entry = ${entry} AND user_id = ${userId} AND skin_id = ${skinId}
              )
              OR (
                (SELECT count(*) FROM ${itemTextures} WHERE user_id = ${userId}) < ${limits.maxPlayerRows}
                AND (SELECT count(*) FROM ${itemTextures}) < ${limits.maxRows}
              )
            )
          `),
      )
      .onConflictDoUpdate({
        target: [itemTextures.entry, itemTextures.userId, itemTextures.skinId],
        set: { texture: sql`excluded.texture` },
      });
};

const sampleWrite = (userId: string, maxSamples: number) => (entry: string, sample: ItemSighting) =>
  db
    .insert(itemSamples)
    .select(
      db
        .select({
          entry: sql`${entry}`.as("entry"),
          userId: sql`${userId}`.as("user_id"),
          sample: sql`${JSON.stringify(sample)}`.as("sample"),
        })
        .from(sql`(select 1) as one`).where(sql`
          EXISTS (SELECT 1 FROM ${itemSeen} WHERE entry = ${entry} AND user_id = ${userId})
          AND (SELECT count(*) FROM ${itemSamples} WHERE entry = ${entry}) < ${maxSamples}
        `),
    )
    .onConflictDoNothing();

export const itemStore: ItemStore["Service"] = {
  record: (userId, modVersion, observations, limits) =>
    run(async () => {
      const folded = fold(observations);
      if (folded.length === 0) {
        return;
      }
      const seen = seenWrite(userId, modVersion, limits);
      const texture = textureWrite(userId, limits);
      const sample = sampleWrite(userId, limits.maxSamples);
      await batch([
        ...folded.map(({ entry, glint, plain }) => seen(entry, glint, plain)),
        ...folded.flatMap(({ entry, textures }) =>
          [...textures].map(([skinId, value]) => texture(entry, skinId, value)),
        ),
        ...folded.flatMap(({ entry, sample: kept }) => (kept === null ? [] : [sample(entry, kept)])),
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
        const samples = new Map([[entry, rows.flatMap(({ sample }) => Option.toArray(decodeSighting(sample)))]]);
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

// @keep void.config.ts sets each binding's limit.
const RATE_BINDINGS: Record<Rate, string> = { player: "RATE_PLAYER", profile: "RATE_PROFILE", items: "RATE_ITEMS" };

/** @keep Cloudflare counts each rate in each of its locations apart and catches up in the background, so a player who
 * moves between locations gets a little more. */
export const limiter: Limiter = {
  allow: (rate, userId) =>
    run(() => requireRuntimeBinding<RateLimit>(RATE_BINDINGS[rate]).limit({ key: userId })).pipe(
      Effect.map(({ success }) => success),
    ),
};
