import { Result, Schema } from "effect";
import { Base64 } from "effect/encoding";

import { lenient } from "./read.ts";

const TEXTURE_URL = /^https?:\/\/textures\.minecraft\.net\/texture\/[0-9a-fA-F]+$/u;
const MAX_TEXTURE = 8192;

const Textures = Schema.Struct({
  textures: Schema.Record(Schema.String, Schema.Struct({ url: Schema.String })),
});
const texturesOf = lenient(Schema.fromJsonString(Textures));

export const safeTexture = (value: unknown): string | undefined => {
  if (typeof value !== "string" || value === "" || value.length > MAX_TEXTURE) {
    return undefined;
  }
  const json = Base64.decodeString(value);
  if (Result.isFailure(json)) {
    return undefined;
  }
  const urls = Object.values(texturesOf(json.success)?.textures ?? {}).map((texture) => texture.url);
  return urls.length > 0 && urls.every((url) => TEXTURE_URL.test(url)) ? value : undefined;
};
