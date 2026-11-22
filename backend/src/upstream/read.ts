import { Option, Schema } from "effect";

export const lenient = <A>(schema: Schema.Decoder<A>): ((value: unknown) => A | undefined) => {
  const decode = Schema.decodeUnknownOption(schema);
  return (value) => Option.getOrUndefined(decode(value));
};

export const text = lenient(Schema.String);
export const flag = lenient(Schema.Boolean);
export const count = lenient(Schema.Finite);
export const strings = lenient(Schema.Array(Schema.String));
export const lists = lenient(Schema.Array(Schema.Unknown));
export const numbers = lenient(Schema.Record(Schema.String, Schema.Finite));

export const numberEntries = (value: unknown): Readonly<Record<string, number>> =>
  Object.fromEntries(
    Object.entries(record(value)).filter((entry): entry is [string, number] => Number.isFinite(entry[1])),
  );

export const scalarEntries = (value: unknown): Readonly<Record<string, string | number | boolean>> =>
  Object.fromEntries(
    Object.entries(record(value)).filter(
      (entry): entry is [string, string | number | boolean] =>
        typeof entry[1] === "string" || typeof entry[1] === "boolean" || Number.isFinite(entry[1]),
    ),
  );
const records = lenient(Schema.Record(Schema.String, Schema.Unknown));

export const record = (value: unknown): Readonly<Record<string, unknown>> => records(value) ?? {};

export const parseColor = (value: unknown): number | undefined => {
  if (typeof value === "number") {
    return Number.isInteger(value) && value >= 0 && value <= 0xffffff ? value : undefined;
  }
  if (typeof value !== "string") {
    return undefined;
  }
  const hex = /^#([0-9a-fA-F]{6})$/u.exec(value);
  if (hex?.[1] !== undefined) {
    return Number.parseInt(hex[1], 16);
  }
  const parts = value.split(",").map((part) => Number(part.trim()));
  const [r, g, b] = parts;
  if (parts.length !== 3 || r === undefined || g === undefined || b === undefined) {
    return undefined;
  }
  return [r, g, b].every((channel) => Number.isInteger(channel) && channel >= 0 && channel <= 255)
    ? (r << 16) | (g << 8) | b
    : undefined;
};

type Stats = Readonly<Record<string, number>>;

export interface StatSet {
  readonly base: Stats;
  readonly additive: Stats;
  readonly multiplicative: Stats;
  readonly increaseMax: Stats;
}

export const statSet = (raw: unknown): StatSet => {
  const set = record(raw);
  return {
    base: numberEntries(set["base"]),
    additive: numberEntries(set["additive"]),
    multiplicative: numberEntries(set["multiplicative"]),
    increaseMax: numberEntries(set["increase_max"]),
  };
};
