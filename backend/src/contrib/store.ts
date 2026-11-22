import type { Effect } from "effect";
import { Context } from "effect";

import type { RecordingRequest, Unknown } from "./model.ts";

export interface SubmissionRow {
  readonly id: string;
  readonly requestId: string;
  readonly userId: string;
  readonly modVersion: string;
  readonly bytes: number;
  readonly createdAt: string;
}

export class Store extends Context.Service<
  Store,
  {
    readonly insertRequest: (request: RecordingRequest) => Effect.Effect<void>;
    readonly requests: Effect.Effect<readonly RecordingRequest[]>;
    readonly request: (id: string) => Effect.Effect<RecordingRequest | null>;
    readonly fill: (id: string) => Effect.Effect<void>;
    readonly close: (id: string) => Effect.Effect<void>;
    readonly insertSkip: (requestId: string, userId: string) => Effect.Effect<void>;
    readonly skips: Effect.Effect<readonly { readonly requestId: string; readonly userId: string }[]>;
    readonly doneBy: (userId: string) => Effect.Effect<readonly string[]>;
    /** @keep False, and nothing is kept, when this player already has a run or the request has `maxRuns`. Decided in
     * the insert, so two players at once cannot both pass. */
    readonly insertSubmission: (row: SubmissionRow, capture: Uint8Array, maxRuns: number) => Effect.Effect<boolean>;
    /** @keep Oldest first, in the same order every time, because a run's letter is its place in this list. */
    readonly submissions: Effect.Effect<readonly SubmissionRow[]>;
    readonly submissionsOf: (requestId: string) => Effect.Effect<readonly SubmissionRow[]>;
    readonly capture: (submissionId: string) => Effect.Effect<Uint8Array | null>;
    /** @keep Counts a known sighting and keeps its first sample. A new one is kept only while fewer than `maxRows` are. */
    readonly countUnknown: (unknown: Omit<Unknown, "count">, maxRows: number) => Effect.Effect<void>;
    readonly unknowns: Effect.Effect<readonly Unknown[]>;
  }
>()("dsmod/contrib/Store") {}
