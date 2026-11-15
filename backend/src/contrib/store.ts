import { Context, Effect } from "effect";

import type { RecordingRequest, Unknown } from "./model.ts";

export interface SubmissionRow {
  readonly id: string;
  readonly requestId: string;
  readonly userId: string;
  readonly modVersion: string;
  readonly bytes: number;
  readonly createdAt: string;
}

/**
 * The rows and files contributions keep. A check that two requests could race past lives here, in one statement the
 * database runs alone, and every other rule lives in `contrib.ts`.
 */
export class Store extends Context.Service<
  Store,
  {
    readonly insertRequest: (request: RecordingRequest) => Effect.Effect<void>;
    readonly requests: Effect.Effect<readonly RecordingRequest[]>;
    readonly request: (id: string) => Effect.Effect<RecordingRequest | null>;
    /** Marks an open request filled. A closed one stays closed. */
    readonly fill: (id: string) => Effect.Effect<void>;
    readonly close: (id: string) => Effect.Effect<void>;
    /** Does nothing when the player already skipped it. */
    readonly insertSkip: (requestId: string, userId: string) => Effect.Effect<void>;
    readonly skips: Effect.Effect<readonly { readonly requestId: string; readonly userId: string }[]>;
    /** The ids of the requests a player skipped or recorded. */
    readonly doneBy: (userId: string) => Effect.Effect<readonly string[]>;
    /** False, and nothing is kept, when this player already has a run for the request. */
    readonly insertSubmission: (row: SubmissionRow, capture: Uint8Array) => Effect.Effect<boolean>;
    /** Oldest first, in the same order every time, because a run's letter is its place in this list. */
    readonly submissions: Effect.Effect<readonly SubmissionRow[]>;
    readonly submissionsOf: (requestId: string) => Effect.Effect<readonly SubmissionRow[]>;
    readonly capture: (submissionId: string) => Effect.Effect<Uint8Array | null>;
    /**
     * Adds one to the count of a sighting that is already known, and keeps its first sample. A new one is kept only
     * while fewer than [maxRows] are.
     */
    readonly countUnknown: (unknown: Omit<Unknown, "count">, maxRows: number) => Effect.Effect<void>;
    readonly unknowns: Effect.Effect<readonly Unknown[]>;
  }
>()("dsmod/contrib/Store") {}

/** A store that forgets everything when the process ends, for the tests. */
export const memoryStore = (): Store["Service"] => {
  const requests: RecordingRequest[] = [];
  const skips: { requestId: string; userId: string }[] = [];
  const submissions: SubmissionRow[] = [];
  const captures = new Map<string, Uint8Array>();
  const unknowns = new Map<string, Unknown>();
  const setStatus = (id: string, status: RecordingRequest["status"], from?: RecordingRequest["status"]) =>
    Effect.sync(() => {
      const index = requests.findIndex((request) => request.id === id);
      const found = requests[index];
      if (found !== undefined && (from === undefined || found.status === from)) {
        requests[index] = { ...found, status };
      }
    });
  return {
    insertRequest: (request) => Effect.sync(() => void requests.push(request)),
    requests: Effect.sync(() => [...requests]),
    request: (id) => Effect.sync(() => requests.find((request) => request.id === id) ?? null),
    fill: (id) => setStatus(id, "filled", "open"),
    close: (id) => setStatus(id, "closed"),
    insertSkip: (requestId, userId) =>
      Effect.sync(() => {
        if (!skips.some((skip) => skip.requestId === requestId && skip.userId === userId)) {
          skips.push({ requestId, userId });
        }
      }),
    skips: Effect.sync(() => [...skips]),
    doneBy: (userId) =>
      Effect.sync(() => [...skips, ...submissions].filter((row) => row.userId === userId).map((row) => row.requestId)),
    insertSubmission: (row, capture) =>
      Effect.sync(() => {
        if (submissions.some((other) => other.requestId === row.requestId && other.userId === row.userId)) {
          return false;
        }
        submissions.push(row);
        captures.set(row.id, capture);
        return true;
      }),
    submissions: Effect.sync(() => [...submissions]),
    submissionsOf: (requestId) => Effect.sync(() => submissions.filter((row) => row.requestId === requestId)),
    capture: (id) => Effect.sync(() => captures.get(id) ?? null),
    countUnknown: (unknown, maxRows) =>
      Effect.sync(() => {
        const key = `${unknown.kind}\n${unknown.signature}`;
        const known = unknowns.get(key);
        if (known !== undefined) {
          unknowns.set(key, { ...known, count: known.count + 1 });
        } else if (unknowns.size < maxRows) {
          unknowns.set(key, { ...unknown, count: 1 });
        }
      }),
    unknowns: Effect.sync(() => [...unknowns.values()]),
  };
};
