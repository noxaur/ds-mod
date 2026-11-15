import { Option, Schema } from "effect";

/**
 * Decodes one field of dragsim.co's data, and gives undefined for a field of the wrong type instead of failing the
 * item, because an item with one odd field still draws.
 */
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

/** The object's entries that are finite numbers, each read alone, so one odd entry does not lose the rest. */
export const numberEntries = (value: unknown): Readonly<Record<string, number>> =>
  Object.fromEntries(
    Object.entries(record(value)).filter((entry): entry is [string, number] => Number.isFinite(entry[1])),
  );

/** The object's entries that are strings, finite numbers or booleans, each read alone. */
export const scalarEntries = (value: unknown): Readonly<Record<string, string | number | boolean>> =>
  Object.fromEntries(
    Object.entries(record(value)).filter(
      (entry): entry is [string, string | number | boolean] =>
        typeof entry[1] === "string" || typeof entry[1] === "boolean" || Number.isFinite(entry[1]),
    ),
  );
const records = lenient(Schema.Record(Schema.String, Schema.Unknown));

/** The object's fields, or none for a value that is not an object. */
export const record = (value: unknown): Readonly<Record<string, unknown>> => records(value) ?? {};

/** `0xRRGGBB` for `#RRGGBB`, for the `r,g,b` the catalog uses, or for a number that is one already. */
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
