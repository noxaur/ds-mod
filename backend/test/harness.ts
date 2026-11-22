import { gzipSync } from "node:zlib";

import { type BetterAuthOptions, betterAuth } from "better-auth";
import { memoryAdapter } from "better-auth/adapters/memory";
import { Effect } from "effect";
import { TestClock } from "effect/testing";

import type { Caller } from "../src/api.ts";
import { makeApp, type Services } from "../src/app.ts";
import type { RequestSpec } from "../src/contrib/model.ts";
import type { HasJoined } from "../src/identity/minecraft.ts";
import { signInOptions } from "../src/identity/options.ts";
import type { ItemSighting } from "../src/items/model.ts";
import { memoryServices } from "../src/memory.ts";

const BACKEND = "http://backend";
export const ADMIN_TOKEN = "secret";

export const testClock = (at: string): Promise<TestClock.TestClock> =>
  Effect.runPromise(Effect.scoped(Effect.tap(TestClock.make(), (clock) => clock.setTime(Date.parse(at)))));

export const advance = (clock: TestClock.TestClock, millis: number): Promise<void> =>
  Effect.runPromise(clock.adjust(millis));

export const cookieCaller = (request: Request): Caller["Service"] => ({
  userId: request.headers.get("cookie"),
  adminToken: ADMIN_TOKEN,
});

export const testApp = (overrides: Partial<Services> = {}, callerOf = cookieCaller) => {
  const app = makeApp(memoryServices(overrides), callerOf);
  const call = async (who: string | null, method: string, path: string, body?: unknown, extra: HeadersInit = {}) => {
    const headers = new Headers(extra);
    if (who === "agent") {
      headers.set("authorization", `Bearer ${ADMIN_TOKEN}`);
    } else if (who !== null) {
      headers.set("cookie", who);
    }
    const init: RequestInit = { method, headers };
    if (body instanceof Uint8Array) {
      headers.set("content-type", "application/octet-stream");
      init.body = body;
    } else if (body !== undefined) {
      headers.set("content-type", "application/json");
      init.body = JSON.stringify(body);
    }
    const response = await app(new Request(`${BACKEND}${path}`, init));
    const type = response.headers.get("content-type") ?? "";
    const value: unknown = type.includes("json") ? await response.json() : new Uint8Array(await response.arrayBuffer());
    return { status: response.status, value };
  };
  return { app, call };
};

export const testAuth = (rateLimit: BetterAuthOptions["rateLimit"], hasJoined?: HasJoined) =>
  betterAuth({
    ...signInOptions(hasJoined),
    baseURL: BACKEND,
    secret: "a-test-secret-that-is-long-enough-for-better-auth",
    database: memoryAdapter({ user: [], session: [], account: [], verification: [] }),
    rateLimit,
  });

export const authRequest = (
  path: string,
  { method = "POST", body, headers = {} }: { method?: string; body?: unknown; headers?: Record<string, string> } = {},
) =>
  new Request(`${BACKEND}/api/auth${path}`, {
    method,
    headers: { "content-type": "application/json", origin: BACKEND, ...headers },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });

export const gzipped = (...bytes: number[]) => new Uint8Array(gzipSync(Uint8Array.from(bytes)));

export const sighting = (entry: string, extra: Partial<ItemSighting> = {}): ItemSighting => ({
  entry,
  material: "minecraft:iron_sword",
  name: [{ text: "Scylla", color: "#FFAA00" }],
  lore: [],
  glint: true,
  data: {},
  ...extra,
});

export const spec: RequestSpec = {
  brief: {
    name: "Golem tiers",
    steps: [
      { do: "Warp to the End", note: "now" },
      { do: "Stand still", seconds: 60 },
    ],
  },
  requirements: [{ kind: "area", value: "The End" }],
  timeoutSeconds: 300,
  wanted: 2,
};
