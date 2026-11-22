import { Clock, Effect } from "effect";

/** @keep Calls a signed-in player may make in a minute. `player` counts every call under `/v1`, and an endpoint that costs
 * more counts against its own as well. The mod sends items once in 20 minutes and asks for one profile at a time, so a
 * real player stays far under each. */
export const RATES = { player: 120, profile: 30, items: 5 } as const;
export type Rate = keyof typeof RATES;

export const RATE_SECONDS = 60;

export interface Limiter {
  readonly allow: (rate: Rate, userId: string) => Effect.Effect<boolean>;
}

/** @keep Counts in fixed windows, as Cloudflare's rate-limit binding does. */
export const memoryLimiter = (): Limiter => {
  const windows = new Map<string, { window: number; calls: number }>();
  return {
    allow: (rate, userId) =>
      Effect.map(Clock.currentTimeMillis, (now) => {
        const key = `${rate}:${userId}`;
        const window = Math.floor(now / (RATE_SECONDS * 1000));
        const last = windows.get(key);
        const calls = last?.window === window ? last.calls + 1 : 1;
        windows.set(key, { window, calls });
        return calls <= RATES[rate];
      }),
  };
};
