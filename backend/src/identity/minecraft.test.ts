import { afterEach, expect, test, vi } from "vite-plus/test";
import { Schema } from "effect";

import { authRequest, testAuth } from "../../test/harness.ts";
import { type HasJoined, mojangHasJoined, websiteRelay } from "./minecraft.ts";
import { trustedKeyHeader } from "./trusted.ts";

const UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
const Signed = Schema.decodeUnknownSync(Schema.Struct({ token: Schema.String, uuid: Schema.String }));
const Challenge = Schema.decodeUnknownSync(Schema.Struct({ serverId: Schema.String }));

afterEach(() => {
  vi.unstubAllGlobals();
});

/**
 * A backend where Mojang saw [joins], each "name serverId" with the name as Mojang spells it, and nothing else.
 * Like Mojang, it matches the name whatever its case, and answers its own spelling.
 */
function backend(joins: string[]) {
  const hasJoined: HasJoined = (name, serverId) => {
    const join = joins.find((seen) => seen.toLowerCase() === `${name} ${serverId}`.toLowerCase());
    return Promise.resolve(join === undefined ? null : { uuid: UUID, name: join.split(" ")[0] ?? "" });
  };
  const auth = testAuth({ enabled: false }, hasJoined);
  const post = (path: string, body: unknown = {}, headers: Record<string, string> = {}) =>
    auth.handler(authRequest(path, { body, headers }));
  const challenge = async () => Challenge(await (await post("/minecraft/challenge")).json()).serverId;
  const sessionOf = (token: string) =>
    auth.api.getSession({ headers: new Headers({ authorization: `Bearer ${token}` }) });
  return { auth, post, challenge, sessionOf };
}

test("a player who joined the challenge gets a session for the account Mojang names", async () => {
  const joins: string[] = [];
  const { post, challenge, sessionOf } = backend(joins);
  const serverId = await challenge();
  joins.push(`Notch ${serverId}`);

  const response = await post("/minecraft/verify", { name: "notch", serverId });
  expect(response.status).toBe(200);
  const { token, uuid } = Signed(await response.json());
  expect(uuid).toBe(UUID);
  expect((await sessionOf(token))?.user.name).toBe("Notch");
});

test("a client that only claims a name is refused", async () => {
  const { post, challenge } = backend([]);
  const response = await post("/minecraft/verify", { name: "Notch", serverId: await challenge() });
  expect(response.status).toBe(401);
});

test("a malformed name or server id is refused before the challenge is spent", async () => {
  const joins: string[] = [];
  const { post, challenge } = backend(joins);
  const serverId = await challenge();
  joins.push(`Notch ${serverId}`);
  const malformed = [{ name: "Not a name!", serverId }, { name: "Notch", serverId: `${serverId}0` }, { name: "Notch" }];
  for (const body of malformed) {
    expect((await post("/minecraft/verify", body)).status).toBe(400);
  }
  expect((await post("/minecraft/verify", { name: "Notch", serverId })).status).toBe(200);
});

test("a server id the backend did not issue is refused, even when Mojang saw the join", async () => {
  const serverId = "ab".repeat(20);
  const { post } = backend([`Notch ${serverId}`]);
  expect((await post("/minecraft/verify", { name: "Notch", serverId })).status).toBe(401);
});

test("a challenge works once", async () => {
  const joins: string[] = [];
  const { post, challenge } = backend(joins);
  const serverId = await challenge();
  joins.push(`Notch ${serverId}`);
  expect((await post("/minecraft/verify", { name: "Notch", serverId })).status).toBe(200);
  expect((await post("/minecraft/verify", { name: "Notch", serverId })).status).toBe(401);
});

test("an expired challenge is refused, even when Mojang saw the join", async () => {
  const joins: string[] = [];
  const { auth, post, challenge } = backend(joins);
  const serverId = await challenge();
  joins.push(`Notch ${serverId}`);
  const context = await auth.$context;
  await context.adapter.updateMany({ model: "verification", where: [], update: { expiresAt: new Date(0) } });
  expect((await post("/minecraft/verify", { name: "Notch", serverId })).status).toBe(401);
});

test("the same account signs in to the same user twice, under its new name", async () => {
  const joins: string[] = [];
  const { post, challenge, sessionOf } = backend(joins);
  const users: string[] = [];
  for (const name of ["Notch", "Notch2"]) {
    const serverId = await challenge();
    joins.push(`${name} ${serverId}`);
    const { token } = Signed(await (await post("/minecraft/verify", { name, serverId })).json());
    const session = await sessionOf(token);
    expect(session?.user.name).toBe(name);
    users.push(session?.user.id ?? "");
  }
  expect(users[0]).toBe(users[1]);
});

test("a player whose first sign-in made their user but not its link signs in to that user", async () => {
  const joins: string[] = [];
  const { auth, post, challenge, sessionOf } = backend(joins);
  const signIn = async () => {
    const serverId = await challenge();
    joins.push(`Notch ${serverId}`);
    const response = await post("/minecraft/verify", { name: "Notch", serverId });
    expect(response.status).toBe(200);
    return (await sessionOf(Signed(await response.json()).token))?.user.id;
  };
  const first = await signIn();
  const context = await auth.$context;
  await context.adapter.deleteMany({ model: "account", where: [] });

  expect(await signIn()).toBe(first);
  expect(await context.adapter.count({ model: "user" })).toBe(1);
  expect(await context.adapter.count({ model: "account" })).toBe(1);
});

test("a signed-in player cannot rename their account or unlink it from Mojang", async () => {
  const joins: string[] = [];
  const { post, challenge, sessionOf } = backend(joins);
  const serverId = await challenge();
  joins.push(`Notch ${serverId}`);
  const { token } = Signed(await (await post("/minecraft/verify", { name: "Notch", serverId })).json());
  const bearer = { authorization: `Bearer ${token}` };

  expect((await post("/update-user", { name: "Victim" }, bearer)).status).toBe(404);
  expect((await post("/unlink-account", { providerId: "minecraft" }, bearer)).status).toBe(404);
  expect((await sessionOf(token))?.user.name).toBe("Notch");
});

test("a challenge or login link nobody used is removed once it has expired", async () => {
  const { auth, challenge } = backend([]);
  const context = await auth.$context;
  const rows = () => context.adapter.count({ model: "verification" });
  await challenge();
  await context.internalAdapter.createVerificationValue({
    identifier: "one-time-token:unused",
    value: "session",
    expiresAt: new Date(Date.now() + 60_000),
  });
  expect(await rows()).toBe(2);
  await context.adapter.updateMany({ model: "verification", where: [], update: { expiresAt: new Date(0) } });
  await challenge();
  expect(await rows()).toBe(1);
});

const mojangSays = (response: Response | Error) => {
  const fetchMock = vi.fn((_url: URL, _init: RequestInit) =>
    response instanceof Error ? Promise.reject(response) : Promise.resolve(response),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
};

test("Mojang's profile is read with the UUID dashed", async () => {
  mojangSays(Response.json({ id: UUID.replaceAll("-", ""), name: "Notch" }));
  expect(await mojangHasJoined()("notch", "ab".repeat(20))).toEqual({ uuid: UUID, name: "Notch" });
});

test("only Mojang's 204 means nobody joined", async () => {
  mojangSays(new Response(null, { status: 204 }));
  expect(await mojangHasJoined()("Notch", "ab".repeat(20))).toBeNull();
});

test.each([
  ["answers 500", new Response(null, { status: 500 }), "Mojang answered 500."],
  ["rate-limits", new Response(null, { status: 429 }), "Mojang answered 429."],
  ["is unreachable", new Error("down"), "Mojang did not answer."],
])("a Mojang that %s is unavailable, not a refusal, and says why", async (_name, response, message) => {
  mojangSays(response);
  await expect(mojangHasJoined()("Notch", "ab".repeat(20))).rejects.toMatchObject({
    statusCode: 503,
    body: { message },
  });
});

test("a backend with the website's key asks Mojang through the website", async () => {
  const fetchMock = mojangSays(new Response(null, { status: 204 }));
  expect(await mojangHasJoined("the-key")("Notch", "ab".repeat(20))).toBeNull();
  const [url, init] = fetchMock.mock.calls[0] ?? [];
  expect(String(url)).toBe(`${websiteRelay}?username=Notch&serverId=${"ab".repeat(20)}`);
  expect(new Headers(init?.headers).get(trustedKeyHeader)).toBe("the-key");
});

test("a relay that refuses is named, not blamed on Mojang", async () => {
  mojangSays(new Response(null, { status: 404 }));
  await expect(mojangHasJoined("wrong-key")("Notch", "ab".repeat(20))).rejects.toMatchObject({
    statusCode: 503,
    body: { message: "The website's Mojang relay answered 404." },
  });
});

test.each([undefined, ""])("a backend without the website's key (%j) asks Mojang itself", async (key) => {
  const fetchMock = mojangSays(new Response(null, { status: 204 }));
  await mojangHasJoined(key)("Notch", "ab".repeat(20));
  const [url, init] = fetchMock.mock.calls[0] ?? [];
  expect(String(url)).toMatch(/^https:\/\/sessionserver\.mojang\.com\//u);
  expect(new Headers(init?.headers).has(trustedKeyHeader)).toBe(false);
});
