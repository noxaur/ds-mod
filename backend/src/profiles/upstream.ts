import type { Effect } from "effect";
import { Context, Schema } from "effect";

export class UpstreamDown extends Schema.TaggedError<UpstreamDown>()("UpstreamDown", {}) {}

export class Upstream extends Context.Service<
  Upstream,
  { readonly get: (path: string) => Effect.Effect<{ readonly status: number; readonly body: unknown }, UpstreamDown> }
>()("dragen/profiles/Upstream") {}
