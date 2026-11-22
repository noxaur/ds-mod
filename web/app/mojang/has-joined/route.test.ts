import { afterEach, expect, test, vi } from "vite-plus/test";

import { relayKeyHeader, trustedKeyHeader } from "../../../shared/website";
import { GET } from "./route";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const serverId = "ab".repeat(20);
const ask = (headers: Record<string, string>) =>
  GET(new Request(`https://website.test/mojang/has-joined?username=Notch&serverId=${serverId}`, { headers }));

const mojangSays = (answer: Response | Error) => {
  const fetchMock = vi.fn<typeof fetch>(() =>
    answer instanceof Error ? Promise.reject(answer) : Promise.resolve(answer),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
};

test("the backend's question reaches Mojang, and Mojang's answer comes back unchanged", async () => {
  vi.stubEnv("MOJANG_RELAY_KEY", "the-key");
  const profile = { id: "069a79f444e94726a5befca90e38aaf5", name: "Notch" };
  const fetchMock = mojangSays(Response.json(profile));
  const answer = await ask({ [relayKeyHeader]: "the-key" });
  expect(answer.status).toBe(200);
  expect(await answer.json()).toEqual(profile);
  expect(fetchMock.mock.calls[0]?.[0]).toEqual(
    new URL(`https://sessionserver.mojang.com/session/minecraft/hasJoined?username=Notch&serverId=${serverId}`),
  );

  mojangSays(new Response(null, { status: 429 }));
  expect((await ask({ [relayKeyHeader]: "the-key" })).status).toBe(429);
});

test.each([
  ["no key", {}],
  ["the wrong key", { [relayKeyHeader]: "wrong" }],
  ["the trusted key in the relay key's header", { [relayKeyHeader]: "the-trusted-key" }],
  ["an empty key", { [relayKeyHeader]: "" }],
])("a caller with %s is not answered", async (_name, headers) => {
  vi.stubEnv("MOJANG_RELAY_KEY", "the-key");
  vi.stubEnv("AUTH_TRUSTED_KEY", "the-trusted-key");
  const fetchMock = mojangSays(new Response(null, { status: 204 }));
  expect((await ask(headers)).status).toBe(404);
  expect(fetchMock).not.toHaveBeenCalled();
});

test("a website without a relay key answers nobody", async () => {
  vi.stubEnv("MOJANG_RELAY_KEY", "");
  vi.stubEnv("AUTH_TRUSTED_KEY", "");
  const fetchMock = mojangSays(new Response(null, { status: 204 }));
  expect((await ask({ [relayKeyHeader]: "" })).status).toBe(404);
  expect(fetchMock).not.toHaveBeenCalled();
});

test("the backend that still sends the trusted key is answered until it redeploys", async () => {
  vi.stubEnv("MOJANG_RELAY_KEY", "the-key");
  vi.stubEnv("AUTH_TRUSTED_KEY", "the-trusted-key");
  mojangSays(new Response(null, { status: 204 }));
  expect((await ask({ [trustedKeyHeader]: "the-trusted-key" })).status).toBe(204);
});

test.each([
  ["a name Mojang never gives", `username=No%20tch&serverId=${serverId}`],
  ["a server id the backend never makes", "username=Notch&serverId=abc"],
  ["a question with nothing in it", ""],
])("%s is refused before it reaches Mojang", async (_name, query) => {
  vi.stubEnv("MOJANG_RELAY_KEY", "the-key");
  const fetchMock = mojangSays(new Response(null, { status: 204 }));
  const answer = await GET(
    new Request(`https://website.test/mojang/has-joined?${query}`, { headers: { [relayKeyHeader]: "the-key" } }),
  );
  expect(answer.status).toBe(400);
  expect(fetchMock).not.toHaveBeenCalled();
});

test("a Mojang that does not answer in time is a 502, and its request is cancelled", async () => {
  vi.useFakeTimers();
  vi.stubEnv("MOJANG_RELAY_KEY", "the-key");
  let cancelled = false;
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => {
            cancelled = true;
            reject(new Error("aborted"));
          });
        }),
    ),
  );
  const answer = ask({ [relayKeyHeader]: "the-key" });
  await vi.advanceTimersByTimeAsync(5_000);
  expect((await answer).status).toBe(502);
  expect(cancelled).toBe(true);
  vi.useRealTimers();
});

test("a Mojang that stalls partway through its answer is a 502, and its request is cancelled", async () => {
  vi.useFakeTimers();
  vi.stubEnv("MOJANG_RELAY_KEY", "the-key");
  let cancelled = false;
  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>((_url, init) => {
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(new TextEncoder().encode('{"id":'));
          init?.signal?.addEventListener("abort", () => {
            cancelled = true;
            controller.error(new Error("aborted"));
          });
        },
      });
      return Promise.resolve(new Response(body, { status: 200 }));
    }),
  );
  const answer = ask({ [relayKeyHeader]: "the-key" });
  await vi.advanceTimersByTimeAsync(5_000);
  expect((await answer).status).toBe(502);
  expect(cancelled).toBe(true);
  vi.useRealTimers();
});

test("a Mojang that cannot be reached is a 502, which the backend reads as Mojang failing", async () => {
  vi.stubEnv("MOJANG_RELAY_KEY", "the-key");
  mojangSays(new Error("down"));
  expect((await ask({ [relayKeyHeader]: "the-key" })).status).toBe(502);
});
