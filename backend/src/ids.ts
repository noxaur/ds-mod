import { Clock, Effect } from "effect";

export const newId = Effect.sync(() => crypto.randomUUID());

export const nowIso = Effect.map(Clock.currentTimeMillis, (millis) => new Date(millis).toISOString());
