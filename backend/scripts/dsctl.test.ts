import { mkdtempSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { gzipSync } from "node:zlib";

import { Effect } from "effect";
import { expect, onTestFinished, test, vi } from "vite-plus/test";

import { advance, spec, testApp, testClock, truncatedGzip } from "../test/harness.ts";
import { dsctl } from "./dsctl.ts";

const clock = await testClock("2026-11-15T12:00:00Z");

function backend(token = "secret") {
  const { app, call } = testApp({ clock });
  const fetch = (input: RequestInfo | URL, init?: RequestInit) => app(new Request(input, init));
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
  const upload = async (player: string, id: string, capture: Uint8Array) => {
    await advance(clock, 1000);
    return call(player, "POST", `/v1/contrib/requests/${id}/submissions?modVersion=1.0.0`, capture);
  };
  const offers = async (player: string) => (await call(player, "GET", "/v1/contrib/requests")).value;
  return { run, lines, failure, call, upload, offers };
}

const directory = () => mkdtempSync(join(tmpdir(), "dsctl-"));

function briefFile(brief: unknown) {
  const file = join(directory(), "brief.json");
  writeFileSync(file, JSON.stringify(brief));
  return file;
}

const golem = spec.brief;

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
  const stem = `golem-tiers-${id}`;
  expect(await lines("pull", id, out)).toEqual([join(out, `${stem}-a.jsonl`), join(out, `${stem}-b.jsonl`)]);
  expect(readdirSync(out).toSorted()).toEqual([`${stem}-a.jsonl`, `${stem}-b.jsonl`]);
  const runs = readdirSync(out)
    .toSorted()
    .map((file) => readFileSync(join(out, file), "utf8"));
  expect(runs).toEqual(['{"type":"step"}\n', '{"type":"chat"}\n']);
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

test("two requests that share the first eight id characters keep both runs", async () => {
  const ids = ["12345678-aaaa-4000-8000-000000000001", "12345678-bbbb-4000-8000-000000000002"] as const;
  vi.spyOn(crypto, "randomUUID").mockReturnValueOnce(ids[0]).mockReturnValueOnce(ids[1]);
  onTestFinished(() => {
    vi.restoreAllMocks();
  });
  const { lines, upload } = backend();
  expect(await lines("create", briefFile(golem))).toEqual([ids[0]]);
  expect(await lines("create", briefFile(golem))).toEqual([ids[1]]);
  await upload("ann", ids[0], gzipSync("first\n"));
  await upload("ann", ids[1], gzipSync("second\n"));
  const out = directory();
  for (const id of ids) {
    await lines("pull", id, out);
  }
  expect(readdirSync(out).toSorted()).toEqual(ids.map((id) => `golem-tiers-${id}-a.jsonl`));
  expect(ids.map((id) => readFileSync(join(out, `golem-tiers-${id}-a.jsonl`), "utf8").trim())).toEqual([
    "first",
    "second",
  ]);
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
  const { lines, call } = backend();
  const report = (sample: string) =>
    call("ann", "POST", "/v1/contrib/unknowns", {
      modVersion: "1.0.0",
      sightings: [{ kind: "chat", signature: sample, sample: `${sample}\nmore` }],
    });
  await report("rare");
  await report("common");
  await report("common");
  expect(await lines("unknowns")).toEqual(['2x  chat  "common"  "common\\nmore"', '1x  chat  "rare"  "rare\\nmore"']);
});

test("a run that cannot be read is named as a problem, and the others are still written", async () => {
  const { run, lines, upload } = backend();
  const [id = ""] = await lines("create", briefFile(golem));
  const broken = (await upload("ann", id, truncatedGzip())).value;
  await upload("bob", id, gzipSync("{}\n"));
  const out = directory();
  const result = await run("pull", id, out);
  expect(result.problems).toEqual([expect.stringMatching(/^Run A .* is not a gzipped capture\.$/)]);
  expect(result.problems[0]).toContain(`(${String(Reflect.get(Object(broken), "id"))})`);
  expect(result.out).toEqual([join(out, `golem-tiers-${id}-b.jsonl`)]);
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
  const { app } = testApp();
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
