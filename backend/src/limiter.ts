import type { Effect } from "effect";

export type Rate = "player" | "profile" | "items";

export interface Limiter {
  readonly allow: (rate: Rate, userId: string) => Effect.Effect<boolean>;
}
