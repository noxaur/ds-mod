import { Effect } from "effect";
import { TestClock } from "effect/testing";

export const testClock = (at: string): Promise<TestClock.TestClock> =>
  Effect.runPromise(Effect.scoped(Effect.tap(TestClock.make(), (clock) => clock.setTime(Date.parse(at)))));

export const advance = (clock: TestClock.TestClock, millis: number): Promise<void> =>
  Effect.runPromise(clock.adjust(millis));
