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
    readonly insertSubmission: (row: SubmissionRow, capture: Uint8Array, maxRuns: number) => Effect.Effect<boolean>;
    readonly submissions: Effect.Effect<readonly SubmissionRow[]>;
    readonly submissionsOf: (requestId: string) => Effect.Effect<readonly SubmissionRow[]>;
    readonly capture: (submissionId: string) => Effect.Effect<Uint8Array | null>;
    readonly countUnknown: (unknown: Omit<Unknown, "count">, maxRows: number) => Effect.Effect<void>;
    readonly unknowns: Effect.Effect<readonly Unknown[]>;
  }
>()("dsmod/contrib/Store") {}
