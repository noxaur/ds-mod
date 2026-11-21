import { Effect } from "effect";
import { afterEach, expect, test, vi } from "vite-plus/test";

import { sessionOf, verifyOneTimeToken } from "./backend";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const backendSays = (body: unknown) => {
  const fetchMock = vi.fn(() => Promise.resolve(Response.json(body)));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
};

const sentHeaders = (fetchMock: ReturnType<typeof backendSays>) => {
  const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
  return new Headers(init.headers);
};

const user = { id: "u", name: "Notch" };
const expiresAt = "2030-01-01T00:00:00.000Z";

test("both sign-in calls send the trusted key when the website has one", async () => {
  vi.stubEnv("AUTH_TRUSTED_KEY", "the-key");
  const verify = backendSays({ session: { token: "t", expiresAt }, user });
  await Effect.runPromise(verifyOneTimeToken("one-time"));
  expect(sentHeaders(verify).get("x-auth-trusted-key")).toBe("the-key");
  expect(sentHeaders(verify).get("content-type")).toBe("application/json");

  const session = backendSays({ session: { expiresAt }, user });
  await Effect.runPromise(sessionOf("token"));
  expect(sentHeaders(session).get("x-auth-trusted-key")).toBe("the-key");
  expect(sentHeaders(session).get("authorization")).toBe("Bearer token");
});

test("without a key in the environment, no key header is sent", async () => {
  vi.stubEnv("AUTH_TRUSTED_KEY", "");
  const session = backendSays({ session: { expiresAt }, user });
  await Effect.runPromise(sessionOf("token"));
  expect(sentHeaders(session).has("x-auth-trusted-key")).toBe(false);
});
