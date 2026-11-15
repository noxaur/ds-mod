import type { Effect } from "effect";
import { Context, Schema } from "effect";

export class UpstreamDown extends Schema.TaggedError<UpstreamDown>()("UpstreamDown", {}) {}

/** dragsim.co, reached at a path such as `/api/player/Name?mode=main`. A test answers it from fixtures. */
export class Upstream extends Context.Service<
  Upstream,
  { readonly get: (path: string) => Effect.Effect<{ readonly status: number; readonly body: unknown }, UpstreamDown> }
>()("dsmod/profiles/Upstream") {}
