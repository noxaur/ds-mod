import { mkdtempSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { gzipSync } from "node:zlib";

import { Effect, Exit } from "effect";
import { expect, test } from "vite-plus/test";

import { makeApp } from "../src/app.ts";
import { memoryStore } from "../src/contrib/store.ts";
import { dsctl } from "./dsctl.ts";

/** A backend with nothing in it, dsctl pointed at it with [token], and a way to upload as a player. */
function backend(token = "secret") {
  const app = makeApp(memoryStore(), (request) => ({ userId: request.headers.get("cookie"), adminToken: "secret" }));
  const fetch = (input: RequestInfo | URL, init?: RequestInit) => app(new Request(input, init));
  const run = (...args: string[]) => Effect.runPromiseExit(dsctl(args, { backend: "http://backend", token, fetch }));
  const lines = async (...args: string[]) => {
    const exit = await run(...args);
    if (Exit.isFailure(exit)) {
      throw new Error(String(exit.cause));
    }
    return exit.value;
  };
  const failure = async (...args: string[]) => {
    const exit = await run(...args);
    return Exit.isFailure(exit) ? String(exit.cause) : "succeeded";
  };
  const upload = (player: string, id: string, capture: Uint8Array) =>
    app(
      new Request(`http://backend/v1/contrib/requests/${id}/submissions?modVersion=1.0.0`, {
        method: "POST",
        headers: { cookie: player, "content-type": "application/octet-stream" },
        body: capture,
      }),
    );
  return { lines, failure, upload };
}

const directory = () => mkdtempSync(join(tmpdir(), "dsctl-"));

function briefFile(brief: unknown) {
  const file = join(directory(), "brief.json");
  writeFileSync(file, JSON.stringify(brief));
  return file;
}

const golem = { name: "Golem tiers", steps: [{ do: "Warp to the End" }, { do: "Stand still", seconds: 60 }] };

test("an agent publishes a brief, players record it, and pull writes one file per player", async () => {
  const { lines, upload } = backend();
  const [id = ""] = await lines("create", briefFile(golem), "--area", "The End", "--wanted", "2");
  await upload("ann", id, gzipSync('{"type":"step"}\n'));
  await upload("bob", id, gzipSync('{"type":"chat"}\n'));

  expect(await lines("list")).toEqual([`${id}  filled  2/2 runs, 0 skipped  Golem tiers`]);
  const out = directory();
  expect(await lines("pull", id, out)).toEqual([join(out, "golem-tiers-a.jsonl"), join(out, "golem-tiers-b.jsonl")]);
  expect(readdirSync(out).toSorted()).toEqual(["golem-tiers-a.jsonl", "golem-tiers-b.jsonl"]);
  expect(readFileSync(join(out, "golem-tiers-a.jsonl"), "utf8")).toBe('{"type":"step"}\n');
});

test("list --json is the backend's report, for an agent to parse", async () => {
  const { lines } = backend();
  const [id] = await lines("create", briefFile(golem));
  const [json = ""] = await lines("list", "--json");
  expect(JSON.parse(json)).toMatchObject([{ id, status: "open", wanted: 3, timeoutSeconds: 600, submissions: [] }]);
});

test("a run that is not gzip inside is skipped, and the others are still written", async () => {
  const { lines, upload } = backend();
  const [id = ""] = await lines("create", briefFile(golem));
  await upload("ann", id, new Uint8Array([0x1f, 0x8b, 1, 2]));
  await upload("bob", id, gzipSync("{}\n"));
  const out = directory();
  const printed = await lines("pull", id, out);
  expect(printed[0]).toMatch(/golem-tiers-a\.jsonl skipped/);
  expect(printed[1]).toBe(join(out, "golem-tiers-b.jsonl"));
});

test("a brief with a key the mod does not know is refused before it is sent, naming the key", async () => {
  const { failure, lines } = backend();
  const typo = { name: "Golem", steps: [{ do: "Wait", secs: 5 }] };
  expect(await failure("create", briefFile(typo))).toMatch(/cannot be published[\s\S]*secs/);
  expect(await failure("create", briefFile(golem), "--wanted", "lots")).toMatch(/wanted/);
  expect(await lines("list")).toEqual([]);
});

test("a wrong token, an unknown id and a bad command each say what went wrong", async () => {
  expect(await backend("wrong").failure("list")).toMatch(/refused the admin token/);
  expect(await backend().failure("close", "nothing")).toMatch(/Nothing on the backend has the id nothing/);
  expect(await backend().failure("pull", "nothing", directory())).toMatch(/No request has the id nothing/);
  expect(await backend().failure("frobnicate")).toMatch(/dsctl create <brief.json>/);
});
