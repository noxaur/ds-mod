import { readFileSync } from "node:fs";

import { expect, test, vi } from "vite-plus/test";
import * as z from "zod";

import { openapi, openapiFile } from "../scripts/openapi.ts";
import { Effect } from "effect";

import { makeApp } from "./app.ts";
import { MAX_UNKNOWNS } from "./contrib/contrib.ts";
import { memoryStore } from "./contrib/store.ts";

const spec = {
  brief: { name: "Golem tiers", steps: [{ do: "Warp to the End" }, { do: "Stand still", seconds: 60 }] },
  requirements: [{ kind: "area", value: "The End" }],
  timeoutSeconds: 300,
  wanted: 2,
};

const invented = (from: number) =>
  Array.from({ length: 100 }, (_, index) => ({ kind: "chat", signature: `s${String(from + index)}`, sample: "x" }));

/** Bytes that start as gzip does, which is all the backend checks of a capture. */
const gz = (...bytes: number[]) => new Uint8Array([0x1f, 0x8b, ...bytes]);

/** A backend with nothing in it, and a way to call it as an agent, as a player, or as nobody. */
function backend() {
  // Who is signed in is what Void would say: here, whoever the test's cookie names. The admin token is "secret".
  const app = makeApp(memoryStore(), (request) => ({
    userId: request.headers.get("cookie"),
    adminToken: "secret",
  }));
  const call = async (who: string | null, method: string, path: string, body?: unknown, extra: HeadersInit = {}) => {
    const headers = new Headers(extra);
    if (who === "agent") {
      headers.set("authorization", "Bearer secret");
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
    const response = await app(new Request(`http://backend${path}`, init));
    const type = response.headers.get("content-type") ?? "";
    const value: unknown = type.includes("json") ? await response.json() : new Uint8Array(await response.arrayBuffer());
    return { status: response.status, value };
  };
  const create = async () =>
    z.object({ id: z.string() }).parse((await call("agent", "POST", "/v1/admin/requests", spec)).value).id;
  const submit = (who: string, id: string, capture = gz(1, 2, 3)) =>
    call(who, "POST", `/v1/contrib/requests/${id}/submissions?modVersion=1.0.0`, capture);
  return { call, create, submit };
}

test("healthz says the backend is up", async () => {
  expect((await backend().call(null, "GET", "/healthz")).status).toBe(204);
});

test("api/openapi.json matches src/api.ts", () => {
  expect(readFileSync(openapiFile, "utf8"), "Run `pnpm openapi` in backend/ and commit the file.").toBe(openapi);
});

test("a player is offered a request until they skip it", async () => {
  const { call, create } = backend();
  const id = await create();
  expect((await call("ann", "GET", "/v1/contrib/requests")).value).toEqual([
    { id, brief: spec.brief, requirements: spec.requirements, timeoutSeconds: 300 },
  ]);
  expect((await call("ann", "POST", `/v1/contrib/requests/${id}/skip`)).status).toBe(204);
  expect((await call("ann", "GET", "/v1/contrib/requests")).value).toEqual([]);
  expect((await call("bob", "GET", "/v1/contrib/requests")).value).toHaveLength(1);
});

test("a request is filled by the wanted number of different players", async () => {
  const { call, create, submit } = backend();
  const id = await create();
  expect((await submit("ann", id)).status).toBe(200);
  expect((await submit("ann", id)).status, "one player counts once").toBe(409);
  expect((await call("bob", "GET", "/v1/contrib/requests")).value).toHaveLength(1);
  expect((await submit("bob", id)).status).toBe(200);
  expect((await call("cat", "GET", "/v1/contrib/requests")).value).toEqual([]);
  expect((await submit("cat", id)).status, "cat accepted before it filled and was still recording").toBe(200);
  expect((await submit("dan", id)).status).toBe(200);
  expect((await submit("eve", id)).status, "twice the wanted number is the end").toBe(409);
  expect((await call("agent", "GET", "/v1/admin/requests")).value).toMatchObject([{ status: "filled" }]);
});

test("a capture that is not gzip is refused and fills nothing", async () => {
  const { call, create, submit } = backend();
  const id = await create();
  expect((await submit("ann", id, new Uint8Array([1, 2, 3]))).status).toBe(400);
  expect((await submit("ann", id, new Uint8Array([]))).status).toBe(400);
  expect((await call("agent", "GET", "/v1/admin/requests")).value).toMatchObject([{ submissions: [] }]);
});

test("a brief the mod would refuse is refused", async () => {
  const { call } = backend();
  const create = (brief: unknown) => call("agent", "POST", "/v1/admin/requests", { ...spec, brief });
  expect((await create({ name: "Golem", steps: [{ do: " " }] })).status, "a blank step").toBe(400);
  expect((await create({ name: "  ", steps: [{ do: "Wait" }] })).status, "a blank name").toBe(400);
  expect(
    (await create({ name: "Golem", steps: [{ do: "Wait", secs: 5 }] })).status,
    "a key the mod does not know",
  ).toBe(400);
  expect((await create({ name: "Golem", steps: [{ do: "Wait", seconds: 5 }] })).status).toBe(200);
});

test("an agent reads every run back, with a letter in place of the player", async () => {
  const { call, create, submit } = backend();
  const id = await create();
  await submit("ann", id, gz(7, 8));
  await call("cat", "POST", `/v1/contrib/requests/${id}/skip`);
  await submit("bob", id, gz(9));

  const { value } = await call("agent", "GET", "/v1/admin/requests");
  expect(JSON.stringify(value)).not.toMatch(/ann|bob|cat/);
  expect(value).toMatchObject([
    {
      id,
      status: "filled",
      skips: 1,
      submissions: [
        { contributor: "A", bytes: 4, modVersion: "1.0.0" },
        { contributor: "B", bytes: 3 },
      ],
    },
  ]);
  const first =
    z.array(z.object({ submissions: z.array(z.object({ id: z.string() })) })).parse(value)[0]?.submissions[0]?.id ?? "";
  expect((await call("agent", "GET", `/v1/admin/submissions/${first}/capture`)).value).toEqual(gz(7, 8));
});

test("a closed request is offered to nobody", async () => {
  const { call, create } = backend();
  const id = await create();
  expect((await call("agent", "POST", `/v1/admin/requests/${id}/close`)).status).toBe(204);
  expect((await call("ann", "GET", "/v1/contrib/requests")).value).toEqual([]);
});

test("the same unknown thing from many players is one row with a count", async () => {
  const { call } = backend();
  const sighting = { kind: "chat", signature: "You found a <item>!", sample: "You found a Dragon Claw!" };
  for (const who of ["ann", "bob", "ann"]) {
    await call(who, "POST", "/v1/contrib/unknowns", { modVersion: "1.0.0", sightings: [sighting] });
  }
  expect((await call("agent", "GET", "/v1/admin/unknowns")).value).toMatchObject([{ ...sighting, count: 3 }]);
});

test("a player cannot call the agent's routes, and nobody gets in without signing in", async () => {
  const { call, create } = backend();
  const id = await create();
  expect((await call("ann", "GET", "/v1/admin/requests")).status).toBe(401);
  expect((await call("ann", "POST", "/v1/admin/requests", spec)).status).toBe(401);
  expect((await call(null, "GET", "/v1/contrib/requests")).status).toBe(401);
  expect((await call(null, "POST", `/v1/contrib/requests/${id}/skip`)).status).toBe(401);
});

test("only the exact admin token makes a caller an agent", async () => {
  const { call } = backend();
  for (const token of ["Bearer ", "Bearer secre", "Bearer secretx", "secret"]) {
    expect((await call(null, "GET", "/v1/admin/requests", undefined, { authorization: token })).status).toBe(401);
  }
});

test("with no admin token set, nobody is an agent", async () => {
  const app = makeApp(memoryStore(), () => ({ userId: null, adminToken: undefined }));
  for (const authorization of ["Bearer ", "Bearer undefined", ""]) {
    const response = await app(new Request("http://backend/v1/admin/requests", { headers: { authorization } }));
    expect(response.status).toBe(401);
  }
});

test("one player sending the same run twice at once still counts once", async () => {
  const { call, create, submit } = backend();
  const id = await create();
  const statuses = (await Promise.all([submit("ann", id), submit("ann", id)])).map((response) => response.status);
  expect(statuses.toSorted((a, b) => a - b)).toEqual([200, 409]);
  expect((await call("bob", "GET", "/v1/contrib/requests")).value, "one player cannot fill it alone").toHaveLength(1);
});

test("a run that lands after the agent closed the request does not reopen it as filled", async () => {
  const { call, create, submit } = backend();
  const id = await create();
  await submit("ann", id);
  await call("agent", "POST", `/v1/admin/requests/${id}/close`);
  expect((await submit("bob", id)).status).toBe(409);
  expect((await call("agent", "GET", "/v1/admin/requests")).value).toMatchObject([{ status: "closed" }]);
});

test("a closed request takes no skips, and a version is a short string", async () => {
  const { call, create } = backend();
  const id = await create();
  await call("agent", "POST", `/v1/admin/requests/${id}/close`);
  await call("ann", "POST", `/v1/contrib/requests/${id}/skip`);
  expect((await call("agent", "GET", "/v1/admin/requests")).value).toMatchObject([{ skips: 0 }]);
  const long = "9".repeat(65);
  const report = { modVersion: long, sightings: [] };
  expect((await call("ann", "POST", "/v1/contrib/unknowns", report)).status).toBe(400);
});

test("a request with no steps or a zero timer is refused", async () => {
  const { call } = backend();
  const bad = { ...spec, brief: { name: "x", steps: [{ do: "Wait", seconds: 0 }] } };
  expect((await call("agent", "POST", "/v1/admin/requests", bad)).status).toBe(400);
  expect((await call("agent", "POST", "/v1/admin/requests", { ...spec, brief: { name: "x", steps: [] } })).status).toBe(
    400,
  );
});

test("invented sightings stop being kept at the cap, and known ones still count", async () => {
  const store = memoryStore();
  const app = makeApp(store, () => ({ userId: "ann", adminToken: undefined }));
  const post = (sightings: unknown[]) =>
    app(
      new Request("http://backend/v1/contrib/unknowns", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ modVersion: "1.0.0", sightings }),
      }),
    );
  for (let from = 0; from < MAX_UNKNOWNS + 200; from += 100) {
    expect((await post(invented(from))).status).toBe(204);
  }
  await post([{ kind: "chat", signature: "s0", sample: "x" }]);
  const kept = await Effect.runPromise(store.unknowns);
  expect(kept).toHaveLength(MAX_UNKNOWNS);
  expect(kept.find((unknown) => unknown.signature === "s0")?.count).toBe(2);
});

test("runs past the 26th are lettered AA, AB, so no two players share a letter", async () => {
  const { call, submit } = backend();
  const created = await call("agent", "POST", "/v1/admin/requests", { ...spec, wanted: 20 });
  const id = z.object({ id: z.string() }).parse(created.value).id;
  for (let player = 0; player < 28; player++) {
    expect((await submit(`player${String(player)}`, id)).status).toBe(200);
  }
  const { value } = await call("agent", "GET", "/v1/admin/requests");
  const letters = z
    .array(z.object({ submissions: z.array(z.object({ contributor: z.string() })) }))
    .parse(value)[0]
    ?.submissions.map((submission) => submission.contributor);
  expect(letters?.slice(24)).toEqual(["Y", "Z", "AA", "AB"]);
});

test("a store that fails answers 500 with nothing in it, and only that reaches the log", async () => {
  const logged = vi.spyOn(console, "error").mockImplementation(() => undefined);
  const { call } = backend();
  expect((await call("ann", "POST", "/v1/contrib/requests/nothing/skip")).status).toBe(404);
  expect((await call("agent", "POST", "/v1/admin/requests", { wanted: 0 })).status).toBe(400);
  expect((await call(null, "GET", "/v1/contrib/requests")).status).toBe(401);
  expect(logged, "a refusal is the client's mistake, not the backend's").not.toHaveBeenCalled();
  const broken = { ...memoryStore(), requests: Effect.die(new Error("D1 is down")) };
  const app = makeApp(broken, () => ({ userId: "ann", adminToken: undefined }));
  const response = await app(new Request("http://backend/v1/contrib/requests"));
  expect(response.status).toBe(500);
  expect(await response.text()).not.toContain("D1");
  expect(String(logged.mock.calls[0]?.[0])).toContain("D1 is down");
  logged.mockRestore();
});
