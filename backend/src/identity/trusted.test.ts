import { expect, test } from "vite-plus/test";

import { authRequest, testAuth } from "../../test/harness.ts";
import { trustedKeyHeader } from "../../../web/shared/website.ts";
import { websiteRules } from "./trusted.ts";

const key = "a-trusted-key-that-only-the-website-knows";
const limit = 3;

const backend = (trustedKey: string | undefined) => {
  const auth = testAuth({
    enabled: true,
    storage: "memory",
    window: 60,
    max: limit,
    customRules: websiteRules(trustedKey),
  });
  return (address: string, path: string, headers: Record<string, string> = {}) =>
    auth.handler(
      authRequest(path, {
        method: path === "/get-session" ? "GET" : "POST",
        headers: { "x-forwarded-for": address, ...headers },
        ...(path === "/get-session" ? {} : { body: { token: "unknown" } }),
      }),
    );
};

const statuses = async (calls: number, send: () => Promise<Response>) => {
  const seen: number[] = [];
  for (let i = 0; i < calls; i++) {
    seen.push((await send()).status);
  }
  return seen;
};

test.each(["/one-time-token/verify", "/get-session"])(
  "calls to %s with the website's key are not counted together under its address",
  async (path) => {
    const call = backend(key);
    const seen = await statuses(limit * 3, () => call("203.0.113.1", path, { [trustedKeyHeader]: key }));
    expect(seen).not.toContain(429);
  },
);

test.each(["/one-time-token/verify", "/get-session"])("calls to %s without the key are limited", async (path) => {
  const call = backend(key);
  const seen = await statuses(limit + 1, () => call("203.0.113.2", path));
  expect(seen.at(-1)).toBe(429);
  expect(seen.slice(0, limit)).not.toContain(429);
});

test("calls with a wrong key are limited", async () => {
  const call = backend(key);
  const seen = await statuses(limit + 1, () => call("203.0.113.3", "/get-session", { [trustedKeyHeader]: "wrong" }));
  expect(seen.at(-1)).toBe(429);
});

test("with no key configured, no header is trusted", async () => {
  const call = backend(undefined);
  for (const sent of ["", "undefined", key]) {
    const address = `203.0.113.${10 + sent.length}`;
    const seen = await statuses(limit + 1, () => call(address, "/get-session", { [trustedKeyHeader]: sent }));
    expect(seen.at(-1)).toBe(429);
  }
});

test("the key does not lift the limit on a path the website never calls", async () => {
  const call = backend(key);
  const seen = await statuses(limit + 1, () =>
    call("203.0.113.4", "/minecraft/challenge", { [trustedKeyHeader]: key }),
  );
  expect(seen.at(-1)).toBe(429);
});
