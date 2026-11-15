import { Effect, Schema } from "effect";
import { and, asc, db, eq, sql } from "void/db";
import { storage } from "void/storage";

import { requests, skips, submissions, unknowns } from "../db/schema.ts";
import { RequestSpec } from "../src/contrib/model.ts";
import type { Store } from "../src/contrib/store.ts";

const decodeSpec = Schema.decodeUnknownSync(RequestSpec);
const decodeStatus = Schema.decodeUnknownSync(Schema.Literals(["open", "filled", "closed"]));
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
      // The row first: its unique index is what stops one player's two requests from both being kept.
      const kept = await db.insert(submissions).values(row).onConflictDoNothing().returning({ id: submissions.id });
      if (kept.length === 0) {
        return false;
      }
      // A row with no file would be a run an agent cannot read, so a failed upload takes its row back.
      // Reaching a missing bucket throws before there is a promise, so the call starts inside one.
      await Promise.resolve()
        .then(() => storage.put(`captures/${row.id}`, capture))
        .catch(async (cause: unknown) => {
          await db.delete(submissions).where(eq(submissions.id, row.id));
          throw cause;
        });
      return true;
    }),
  submissions: run(() => db.select().from(submissions).orderBy(asc(submissions.createdAt))),
  submissionsOf: (requestId) =>
    run(() =>
      db.select().from(submissions).where(eq(submissions.requestId, requestId)).orderBy(asc(submissions.createdAt)),
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
