import { Schema, Struct } from "effect";

/**
 * The mod refuses a blank line, so the backend must too, or it stores a request no player is offered. The `u` flag is
 * what lets the pattern into the contract.
 */
const Shown = Schema.String.check(Schema.isPattern(/\S/u));

/** One thing to play. With `seconds` the step is timed and moves on by itself. The mod reads the same shape from `-Pbrief`. */
export const Step = Schema.Struct({
  do: Shown,
  note: Schema.optionalKey(Schema.String),
  seconds: Schema.optionalKey(Schema.Int.check(Schema.isGreaterThan(0))),
});

// A plain list with a minimum, which a generated client reads as a list and not as a tuple.
export const Brief = Schema.Struct({ name: Shown, steps: Schema.Array(Step).check(Schema.isMinLength(1)) });

/**
 * What a player must have before the mod offers them a request. The mod checks it, because only the mod sees the
 * player's game, and it treats a kind it does not know as not met.
 */
export const Requirement = Schema.Struct({ kind: Schema.Literals(["area"]), value: Schema.NonEmptyString });

export const RequestSpec = Schema.Struct({
  brief: Brief,
  requirements: Schema.Array(Requirement),
  /** The run is thrown away when it takes longer than this. */
  timeoutSeconds: Schema.Int.check(Schema.isBetween({ minimum: 10, maximum: 3600 })),
  /** How many different players should record it, since one player's run proves little. */
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

/** What the mod is offered: enough to check the requirements, show the brief and time the run. */
export const Offer = RecordingRequest.mapFields(Struct.pick(["id", "brief", "requirements", "timeoutSeconds"]));

export const Submission = Schema.Struct({
  id: Schema.String,
  requestId: Schema.String,
  /** A letter per player within one request, so an agent can tell runs apart without learning who played. */
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

/** Something the mod saw and had no handling for. `signature` is what makes two sightings the same thing. */
export const Sighting = Schema.Struct({
  kind: Schema.String.check(Schema.isMaxLength(64)),
  signature: Schema.String.check(Schema.isMaxLength(256)),
  sample: Schema.String.check(Schema.isMaxLength(4096)),
});

export const Unknown = Schema.Struct({
  ...Sighting.fields,
  count: Schema.Int,
  /** The version of the mod that reported it first. */
  modVersion: Schema.String,
  firstSeen: Schema.String,
});
export type Unknown = typeof Unknown.Type;
