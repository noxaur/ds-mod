import { Schema, Struct } from "effect";

/** @keep The mod refuses a blank line, so the backend must too. The `u` flag lets the pattern into the contract. */
const Shown = Schema.String.check(Schema.isPattern(/\S/u));

export const Step = Schema.Struct({
  do: Shown,
  note: Schema.optionalKey(Schema.String),
  seconds: Schema.optionalKey(Schema.Int.check(Schema.isGreaterThan(0))),
});

// @keep A plain list with a minimum, which a generated client reads as a list and not as a tuple.
export const Brief = Schema.Struct({ name: Shown, steps: Schema.Array(Step).check(Schema.isMinLength(1)) });

/** @keep The mod checks requirements, since only it sees the player's game, and treats an unknown kind as not met. */
export const Requirement = Schema.Struct({ kind: Schema.Literals(["area"]), value: Schema.NonEmptyString });

export const RequestSpec = Schema.Struct({
  brief: Brief,
  requirements: Schema.Array(Requirement),
  timeoutSeconds: Schema.Int.check(Schema.isBetween({ minimum: 10, maximum: 3600 })),
  wanted: Schema.Int.check(Schema.isBetween({ minimum: 1, maximum: 20 })),
});
export type RequestSpec = typeof RequestSpec.Type;

export const RecordingRequest = Schema.Struct({
  ...RequestSpec.fields,
  id: Schema.String,
  status: Schema.Literals(["open", "filled", "closed"]),
  createdAt: Schema.String,
});
export type RecordingRequest = typeof RecordingRequest.Type;

export const Offer = RecordingRequest.mapFields(Struct.pick(["id", "brief", "requirements", "timeoutSeconds"]));

export const Submission = Schema.Struct({
  id: Schema.String,
  requestId: Schema.String,
  /** @keep A letter per player within one request, so an agent never learns who played. */
  contributor: Schema.String,
  modVersion: Schema.String,
  bytes: Schema.Int,
  createdAt: Schema.String,
});

export const RequestReport = Schema.Struct({
  ...RecordingRequest.fields,
  skips: Schema.Int,
  submissions: Schema.Array(Submission),
});

export const Sighting = Schema.Struct({
  kind: Schema.String.check(Schema.isMaxLength(64)),
  signature: Schema.String.check(Schema.isMaxLength(256)),
  sample: Schema.String.check(Schema.isMaxLength(4096)),
});

export const Unknown = Schema.Struct({
  ...Sighting.fields,
  count: Schema.Int,
  modVersion: Schema.String,
  firstSeen: Schema.String,
});
export type Unknown = typeof Unknown.Type;
