import { Effect } from "effect";
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";
import { begin, finish, type Jar } from "./signIn";

const expiresAt = "2030-01-02T03:04:05.000Z";
const verified = { session: { token: "session-secret", expiresAt }, user: { name: "Steve" } };
const current = { session: { expiresAt }, user: { name: "Steve" } };
const options = { httpOnly: true, sameSite: "lax", secure: false };
const dropPending = ["dsmod_pending", "", { ...options, expires: new Date(0), path: "/login" }];

const backendSays = (status: number, body: unknown) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(() => Promise.resolve(new Response(JSON.stringify(body), { status }))),
  );

/** A browser's cookies: what it sent, and what the login set in answer. */
const jarWith = (sent: Record<string, string> = {}) => {
  const set = vi.fn();
  const jar: Jar = { get: (name) => (name in sent ? { value: sent[name] ?? "" } : undefined), set };
  return { jar, set };
};

const pending = { dsmod_pending: "session-secret" };

beforeEach(() => vi.useFakeTimers({ now: new Date("2029-06-01T00:00:00.000Z") }));

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("begin", () => {
  it("holds the session in a pending cookie and asks whose it is", async () => {
    backendSays(200, verified);
    const { jar, set } = jarWith();
    expect(await Effect.runPromise(begin("one-time-secret", jar))).toBe("Steve");
    expect(set.mock.calls).toEqual([
      [
        "dsmod_pending",
        "session-secret",
        { ...options, expires: new Date("2029-06-01T00:05:00.000Z"), path: "/login" },
      ],
    ]);
    const [url, init] = vi.mocked(fetch).mock.calls[0] ?? [];
    expect(url).toBe("http://localhost:8787/api/auth/one-time-token/verify");
    expect(init?.body).toBe(JSON.stringify({ token: "one-time-secret" }));
  });

  it("never keeps the pending cookie past the session's own end", async () => {
    backendSays(200, { ...verified, session: { token: "session-secret", expiresAt: "2029-06-01T00:01:00.000Z" } });
    const { jar, set } = jarWith();
    await Effect.runPromise(begin("one-time-secret", jar));
    expect(set.mock.calls[0]?.[2]).toMatchObject({ expires: new Date("2029-06-01T00:01:00.000Z") });
  });

  it("marks its cookies Secure in production", async () => {
    vi.stubEnv("NODE_ENV", "production");
    backendSays(200, verified);
    const { jar, set } = jarWith();
    await Effect.runPromise(begin("one-time-secret", jar));
    expect(set.mock.calls[0]?.[2]).toMatchObject({ secure: true });
  });

  it.each([
    ["refuses the token", () => backendSays(400, { message: "Invalid token" })],
    ["answers something unexpected", () => backendSays(200, { nope: true })],
    [
      "is unreachable",
      () =>
        vi.stubGlobal(
          "fetch",
          vi.fn(() => Promise.reject(new Error("down"))),
        ),
    ],
  ])("sets no cookie and names nobody when the backend %s", async (_name, backend) => {
    backend();
    const { jar, set } = jarWith();
    expect(await Effect.runPromise(begin("one-time-secret", jar))).toBeNull();
    expect(set).not.toHaveBeenCalled();
  });

  it("finds the pending session again when the page is reloaded", async () => {
    backendSays(200, current);
    const { jar, set } = jarWith(pending);
    expect(await Effect.runPromise(begin("", jar))).toBe("Steve");
    expect(set).not.toHaveBeenCalled();
    expect(vi.mocked(fetch).mock.calls[0]?.[1]?.headers).toEqual({ authorization: "Bearer session-secret" });
  });

  it("finds the pending session when a reload sends the spent token again", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) =>
        Promise.resolve(
          url.endsWith("/verify") ? new Response("{}", { status: 400 }) : new Response(JSON.stringify(current)),
        ),
      ),
    );
    const { jar, set } = jarWith(pending);
    expect(await Effect.runPromise(begin("one-time-secret", jar))).toBe("Steve");
    expect(set).not.toHaveBeenCalled();
  });

  it("names nobody and asks the backend nothing without a token or a pending session", async () => {
    backendSays(200, current);
    expect(await Effect.runPromise(begin("", jarWith({ dsmod_session: "old" }).jar))).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
  });
});

describe("finish", () => {
  it("signs in on Continue and clears the pending cookie", async () => {
    backendSays(200, current);
    const { jar, set } = jarWith(pending);
    expect(await Effect.runPromise(finish("continue", jar))).toBe("Steve");
    expect(set.mock.calls).toEqual([
      dropPending,
      ["dsmod_session", "session-secret", { ...options, expires: new Date(expiresAt), path: "/" }],
    ]);
    const [url, init] = vi.mocked(fetch).mock.calls[0] ?? [];
    expect(url).toBe("http://localhost:8787/api/auth/get-session");
    expect(init?.headers).toEqual({ authorization: "Bearer session-secret" });
  });

  it.each(["cancel", "", "CONTINUE"])("clears the pending cookie and signs nobody in on %j", async (choice) => {
    backendSays(200, current);
    const { jar, set } = jarWith(pending);
    expect(await Effect.runPromise(finish(choice, jar))).toBeNull();
    expect(set.mock.calls).toEqual([dropPending]);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("signs nobody in when the backend does not know the pending session", async () => {
    backendSays(200, null);
    const { jar, set } = jarWith(pending);
    expect(await Effect.runPromise(finish("continue", jar))).toBeNull();
    expect(set.mock.calls).toEqual([dropPending]);
  });

  it("signs nobody in without a pending cookie", async () => {
    backendSays(200, current);
    const { jar, set } = jarWith({ dsmod_session: "old" });
    expect(await Effect.runPromise(finish("continue", jar))).toBeNull();
    expect(set.mock.calls).toEqual([dropPending]);
    expect(fetch).not.toHaveBeenCalled();
  });
});
