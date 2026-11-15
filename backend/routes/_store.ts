import { Effect, Schema } from "effect";
import { and, asc, db, eq, sql } from "void/db";
import { storage } from "void/storage";

import { requests, skips, submissions, unknowns } from "../db/schema.ts";
import { Request, RequestSpec } from "../src/contrib/model.ts";
import type { Store } from "../src/contrib/store.ts";

const decodeSpec = Schema.decodeUnknownSync(RequestSpec);
const decodeStatus = Schema.decodeUnknownSync(Request.fields.status);
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
