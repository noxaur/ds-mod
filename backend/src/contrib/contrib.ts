import { Effect } from "effect";
import { HttpApiError } from "effect/http-api";

import type { RequestSpec } from "./model.ts";
import { Store } from "./store.ts";

/**
 * Any signed-in client can invent sightings, so the table stops taking new ones here and only counts the ones it
 * has. An agent reads them long before a real game has this many unknown things.
 */
export const MAX_UNKNOWNS = 5000;

const now = Effect.sync(() => new Date().toISOString());
const newId = Effect.sync(() => crypto.randomUUID());

export const createRequest = Effect.fn(function* (spec: RequestSpec) {
  const store = yield* Store;
  const request = { ...spec, id: yield* newId, status: "open" as const, createdAt: yield* now };
  yield* store.insertRequest(request);
  return request;
});

/** The open requests a player has neither recorded nor skipped. The mod drops the ones whose requirements they miss. */
export const offersFor = Effect.fn(function* (userId: string) {
  const store = yield* Store;
  const done = new Set(yield* store.doneBy(userId));
  return (yield* store.requests)
    .filter((request) => request.status === "open" && !done.has(request.id))
    .map(({ id, brief, requirements, timeoutSeconds }) => ({ id, brief, requirements, timeoutSeconds }));
});

const requestOf = Effect.fn(function* (id: string) {
  const request = yield* (yield* Store).request(id);
  if (request === null) {
    return yield* new HttpApiError.NotFound();
  }
  return request;
});

export const skip = Effect.fn(function* (requestId: string, userId: string) {
  const request = yield* requestOf(requestId);
  // A request nobody is offered any more needs no hiding.
  if (request.status === "open") {
    yield* (yield* Store).insertSkip(requestId, userId);
  }
});

/**
 * Keeps one player's run. A request is filled once enough different players recorded it, which only stops it being
 * offered: a player who accepted before that is still recording, so their run is kept too, up to twice the wanted
 * number. One player sending twice at the same moment is different: the store keeps one run per player, so a
 * single client cannot fill a request alone.
 */
export const submit = Effect.fn(function* (requestId: string, userId: string, modVersion: string, capture: Uint8Array) {
  // The mod sends gzip, and an agent's tools read nothing else.
  const gzipped = capture[0] === 0x1f && capture[1] === 0x8b;
  if (!gzipped) {
    return yield* new HttpApiError.BadRequest();
  }
  const store = yield* Store;
  const request = yield* requestOf(requestId);
  if (request.status === "closed" || (yield* store.submissionsOf(requestId)).length >= request.wanted * 2) {
    return yield* new HttpApiError.Conflict();
  }
  const id = yield* newId;
  const kept = yield* store.insertSubmission(
    { id, requestId, userId, modVersion, bytes: capture.byteLength, createdAt: yield* now },
    capture,
  );
  if (!kept) {
    return yield* new HttpApiError.Conflict();
  }
  if (request.status === "open" && (yield* store.submissionsOf(requestId)).length >= request.wanted) {
    yield* store.fill(requestId);
  }
  return { id };
});

export const close = Effect.fn(function* (requestId: string) {
  yield* requestOf(requestId);
  yield* (yield* Store).close(requestId);
});

/** A, B, ... Z, then AA, AB, as spreadsheet columns go, so the 27th player is not mistaken for the first. */
const letter = (index: number): string =>
  (index >= 26 ? letter(Math.floor(index / 26) - 1) : "") + String.fromCharCode(65 + (index % 26));

/** Every request with its runs. A player is a letter here, in the order they submitted, and never an id. */
export const reports = Effect.gen(function* () {
  const store = yield* Store;
  const skips = yield* store.skips;
  const submissions = yield* store.submissions;
  return (yield* store.requests).map((request) => ({
    ...request,
    skips: skips.filter((row) => row.requestId === request.id).length,
    submissions: submissions
      .filter((row) => row.requestId === request.id)
      .map((row, index) => ({
        id: row.id,
        requestId: row.requestId,
        modVersion: row.modVersion,
        bytes: row.bytes,
        createdAt: row.createdAt,
        contributor: letter(index),
      })),
  }));
});

export const captureOf = Effect.fn(function* (submissionId: string) {
  const capture = yield* (yield* Store).capture(submissionId);
  if (capture === null) {
    return yield* new HttpApiError.NotFound();
  }
  return capture;
});

export const report = Effect.fn(function* (
  modVersion: string,
  sightings: readonly { readonly kind: string; readonly signature: string; readonly sample: string }[],
) {
  const store = yield* Store;
  const firstSeen = yield* now;
  return yield* Effect.forEach(
    sightings,
    (sighting) => store.countUnknown({ ...sighting, modVersion, firstSeen }, MAX_UNKNOWNS),
    { discard: true },
  );
});

export const unknowns = Effect.flatMap(Store, (store) => store.unknowns);
