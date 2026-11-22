import type { Effect } from "effect";

// @keep `player` counts every call a signed-in player makes, and an endpoint that costs more counts against its own too.
export type Rate = "player" | "profile" | "items";

export interface Limiter {
  readonly allow: (rate: Rate, userId: string) => Effect.Effect<boolean>;
}
