import { mkdtempSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { gzipSync } from "node:zlib";

import { Effect } from "effect";
import { expect, test } from "vite-plus/test";

import { makeApp } from "../src/app.ts";
import { memoryStore } from "../src/contrib/store.ts";
import { dsctl } from "./dsctl.ts";

/** A backend with nothing in it, dsctl pointed at it with [token], and a way to call it as a player. */
function backend(token = "secret") {
  const app = makeApp(memoryStore(), (request) => ({ userId: request.headers.get("cookie"), adminToken: "secret" }));
  const fetch = (input: RequestInfo | URL, init?: RequestInit) => app(new Request(input, init));
  /** What dsctl prints, or the message it fails with, which the real command prints on stderr. */
  const run = (...args: string[]) =>
    Effect.runPromise(
      dsctl(args, { backend: "http://backend", token, fetch }).pipe(
        Effect.catch((failed) => Effect.succeed({ out: [], problems: [], failed: failed.message })),
      ),
    );
  const lines = async (...args: string[]) => {
    const result = await run(...args);
    expect("failed" in result ? result.failed : result.problems, `dsctl ${args.join(" ")}`).toEqual([]);
    return result.out;
  };
  const failure = async (...args: string[]) => {
    const result = await run(...args);
    return "failed" in result ? result.failed : "succeeded";
  };
  const asPlayer = (player: string, path: string, body: BodyInit, type: string) =>
    app(
      new Request(`http://backend${path}`, { method: "POST", headers: { cookie: player, "content-type": type }, body }),
    );
  const upload = (player: string, id: string, capture: Uint8Array) =>
    asPlayer(player, `/v1/contrib/requests/${id}/submissions?modVersion=1.0.0`, capture, "application/octet-stream");
  const offers = async (player: string) =>
    (await app(new Request("http://backend/v1/contrib/requests", { headers: { cookie: player } }))).json();
  return { run, lines, failure, asPlayer, upload, offers };
}

const directory = () => mkdtempSync(join(tmpdir(), "dsctl-"));

function briefFile(brief: unknown) {
  const file = join(directory(), "brief.json");
  writeFileSync(file, JSON.stringify(brief));
  return file;
}

const golem = { name: "Golem tiers", steps: [{ do: "Warp to the End" }, { do: "Stand still", seconds: 60 }] };

test("an agent publishes a brief, players record it, and pull writes one file per player", async () => {
  const { lines, upload, offers } = backend();
  const [id = ""] = await lines("create", briefFile(golem), "--area", "The End", "--wanted", "2", "--timeout", "120");
  expect(await offers("ann")).toEqual([
    { id, brief: golem, requirements: [{ kind: "area", value: "The End" }], timeoutSeconds: 120 },
  ]);
  await upload("ann", id, gzipSync('{"type":"step"}\n'));
  await upload("bob", id, gzipSync('{"type":"chat"}\n'));

  expect(await lines("list")).toEqual([`${id}  filled  2/2 runs, 0 skipped  Golem tiers`]);
  const out = directory();
  const stem = `golem-tiers-${id.slice(0, 8)}`;
  expect(await lines("pull", id, out)).toEqual([join(out, `${stem}-a.jsonl`), join(out, `${stem}-b.jsonl`)]);
  expect(readdirSync(out).toSorted()).toEqual([`${stem}-a.jsonl`, `${stem}-b.jsonl`]);
  expect(readFileSync(join(out, `${stem}-a.jsonl`), "utf8")).toBe('{"type":"step"}\n');
});

test("two briefs with the same name pulled into one directory keep every run", async () => {
  const { lines, upload } = backend();
  const ids = [(await lines("create", briefFile(golem)))[0] ?? "", (await lines("create", briefFile(golem)))[0] ?? ""];
  for (const id of ids) {
    await upload("ann", id, gzipSync(`${id}\n`));
  }
  const out = directory();
  for (const id of ids) {
    await lines("pull", id, out);
  }
  expect(
    readdirSync(out)
      .map((file) => readFileSync(join(out, file), "utf8").trim())
      .toSorted(),
  ).toEqual(ids.toSorted());
});

test("list --json is the backend's report, for an agent to parse", async () => {
  const { lines } = backend();
  const [id] = await lines("create", briefFile(golem));
  const [json = ""] = await lines("list", "--json");
  expect(JSON.parse(json)).toMatchObject([{ id, status: "open", wanted: 3, timeoutSeconds: 600, submissions: [] }]);
});

test("close stops a request being offered and prints its id", async () => {
  const { lines, offers } = backend();
  const [id = ""] = await lines("create", briefFile(golem));
  expect(await lines("close", id)).toEqual([id]);
  expect(await offers("ann")).toEqual([]);
});

test("unknowns lists the most seen first, one line each", async () => {
  const { lines, asPlayer } = backend();
  const report = (sample: string) =>
    asPlayer(
      "ann",
      "/v1/contrib/unknowns",
      JSON.stringify({
        modVersion: "1.0.0",
        sightings: [{ kind: "chat", signature: sample, sample: `${sample}\nmore` }],
      }),
      "application/json",
    );
  await report("rare");
  await report("common");
  await report("common");
  expect(await lines("unknowns")).toEqual(['2x  chat  "common"  "common\\nmore"', '1x  chat  "rare"  "rare\\nmore"']);
});

test("a run that cannot be read is named as a problem, and the others are still written", async () => {
  const { run, lines, upload } = backend();
  const [id = ""] = await lines("create", briefFile(golem));
  await upload("ann", id, new Uint8Array([0x1f, 0x8b, 1, 2]));
  await upload("bob", id, gzipSync("{}\n"));
  const out = directory();
  expect(await run("pull", id, out)).toEqual({
    out: [join(out, `golem-tiers-${id.slice(0, 8)}-b.jsonl`)],
    problems: [expect.stringMatching(/^Run A .* is not a gzipped capture\.$/)],
  });
});

test("a brief that cannot be read or published says which, naming the problem", async () => {
  const { failure, lines } = backend();
  const typo = { name: "Golem", steps: [{ do: "Wait", secs: 5 }] };
  expect(await failure("create", briefFile(typo))).toMatch(/cannot be published[\s\S]*secs/);
  expect(await failure("create", briefFile(golem), "--wanted", "lots")).toMatch(/cannot be published[\s\S]*wanted/);
  expect(await failure("create", join(directory(), "missing.json"))).toMatch(/^Could not read .*missing\.json/);
  expect(await lines("list")).toEqual([]);
});

test("a wrong token, an unknown id and a bad command each say what went wrong", async () => {
  expect(await backend("wrong").failure("list")).toBe("The backend refused the admin token. Set DSMOD_ADMIN_TOKEN.");
  expect(await backend().failure("--json", "close", "nothing")).toBe("No request has the id nothing.");
  expect(await backend().failure("pull", "nothing", directory())).toBe("No request has the id nothing.");
  expect(await backend().failure("frobnicate")).toMatch(/^dsctl asks players/);
});

test("a body the backend refuses is named by where and what it expected", async () => {
  const app = makeApp(memoryStore(), () => ({ userId: null, adminToken: "secret" }));
  // A dsctl from another version than the backend's: its brief carries a key the backend does not know.
  const fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const body: unknown = await new Request(input, init).json();
    return app(new Request(input, { ...init, body: JSON.stringify({ ...Object(body), retries: 2 }) }));
  };
  const failed = await Effect.runPromise(
    dsctl(["create", briefFile(golem)], { backend: "http://backend", token: "secret", fetch }).pipe(
      Effect.match({ onFailure: (failure) => failure.message, onSuccess: () => "succeeded" }),
    ),
  );
  expect(failed).toBe("The backend refused what dsctl sent:\n  payload.retries: Expected no excess property");
});
