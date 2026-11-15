import { betterAuth } from "better-auth";
import { memoryAdapter } from "better-auth/adapters/memory";
import { bearer } from "better-auth/plugins/bearer";
import { expect, test } from "vite-plus/test";
import * as z from "zod";

import { type HasJoined, minecraft } from "./minecraft.ts";

const UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
const Signed = z.object({ token: z.string(), uuid: z.string() });
const Challenge = z.object({ serverId: z.string() });

/** A backend where Mojang saw [joins], a list of "name serverId", and nothing else. */
function backend(joins: string[]) {
  const hasJoined: HasJoined = (name, serverId) =>
    Promise.resolve(joins.includes(`${name} ${serverId}`) ? { uuid: UUID, name } : null);
  const auth = betterAuth({
    baseURL: "http://backend",
    secret: "a-test-secret-that-is-long-enough-for-better-auth",
    database: memoryAdapter({ user: [], session: [], account: [], verification: [] }),
    rateLimit: { enabled: false },
    plugins: [minecraft(hasJoined), bearer()],
  });
  const post = (path: string, body: unknown = {}) =>
    auth.handler(
      new Request(`http://backend/api/auth${path}`, {
        method: "POST",
        headers: { "content-type": "application/json", origin: "http://backend" },
        body: JSON.stringify(body),
      }),
    );
  const challenge = async () => Challenge.parse(await (await post("/minecraft/challenge")).json()).serverId;
  return { auth, post, challenge };
}

test("a player who joined the challenge gets a session for their own account", async () => {
  const joins: string[] = [];
  const { auth, post, challenge } = backend(joins);
  const serverId = await challenge();
  joins.push(`Notch ${serverId}`);

  const response = await post("/minecraft/verify", { name: "Notch", serverId });
  expect(response.status).toBe(200);
  const { token, uuid } = Signed.parse(await response.json());
  expect(uuid).toBe(UUID);

  const session = await auth.api.getSession({ headers: new Headers({ authorization: `Bearer ${token}` }) });
  expect(session?.user.name).toBe("Notch");
});

test("a client that only claims a name is refused", async () => {
  const { post, challenge } = backend([]);
  const response = await post("/minecraft/verify", { name: "Notch", serverId: await challenge() });
  expect(response.status).toBe(401);
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

test("the same account signs in to the same user twice, under its new name", async () => {
  const joins: string[] = [];
  const { auth, post, challenge } = backend(joins);
  const users: string[] = [];
  for (const name of ["Notch", "Notch2"]) {
    const serverId = await challenge();
    joins.push(`${name} ${serverId}`);
    const { token } = Signed.parse(await (await post("/minecraft/verify", { name, serverId })).json());
    const session = await auth.api.getSession({ headers: new Headers({ authorization: `Bearer ${token}` }) });
    expect(session?.user.name).toBe(name);
    users.push(session?.user.id ?? "");
  }
  expect(users[0]).toBe(users[1]);
});

test("a challenge nobody answered is removed once it has expired", async () => {
  const { auth, challenge } = backend([]);
  const context = await auth.$context;
  const rows = () => context.adapter.count({ model: "verification" });
  await challenge();
  await challenge();
  expect(await rows()).toBe(2);
  await context.adapter.updateMany({ model: "verification", where: [], update: { expiresAt: new Date(0) } });
  await challenge();
  expect(await rows()).toBe(1);
});
