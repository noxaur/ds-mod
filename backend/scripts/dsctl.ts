import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { parseArgs } from "node:util";
import { gunzipSync } from "node:zlib";

import { Data, Effect, Schema } from "effect";

import { Brief, RequestReport, Unknown } from "../src/contrib/model.ts";

const USAGE = `dsctl asks players for captures and reads them back.

  dsctl create <brief.json> [--area <name>]... [--wanted 3] [--timeout 600]
      Publishes a brief, the same file runClient -Pbrief takes. Players in each --area are asked, until
      --wanted of them recorded it. A run longer than --timeout seconds is thrown away.
  dsctl list                 Every request with its runs and how many players skipped it.
  dsctl close <request id>   Stops offering a request.
  dsctl pull <request id> <directory>
      Writes each run as <directory>/<brief name>-<letter>.jsonl, ready for cutFixture.
  dsctl unknowns             What the mod saw and had no handling for, most seen first.

DSMOD_BACKEND is the backend's address (http://localhost:8787 by default) and DSMOD_ADMIN_TOKEN its admin token.`;

const backend = process.env["DSMOD_BACKEND"] ?? "http://localhost:8787";
const token = process.env["DSMOD_ADMIN_TOKEN"] ?? "";

class Failed extends Data.TaggedError("Failed")<{ readonly message: string }> {}

const call = (method: string, path: string, body?: unknown) =>
  Effect.tryPromise({
    try: () =>
      fetch(`${backend}${path}`, {
        method,
        headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      }),
    catch: (cause) => new Failed({ message: `Could not reach ${backend}: ${String(cause)}` }),
  }).pipe(
    Effect.filterOrFail(
      (response) => response.ok,
      (response) =>
        new Failed({
          message:
            response.status === 401
              ? "The backend refused the admin token. Set DSMOD_ADMIN_TOKEN."
              : `${method} ${path} answered ${String(response.status)}.`,
        }),
    ),
  );

const json = <S extends Schema.Top>(schema: S, response: Response) =>
  Effect.tryPromise({
    try: () => response.json(),
    catch: () => new Failed({ message: "The backend answered something that is not JSON." }),
  }).pipe(
    Effect.flatMap(Schema.decodeUnknownEffect(schema)),
    Effect.mapError((error) => new Failed({ message: error.message })),
  );

const reports = Effect.flatMap(call("GET", "/v1/admin/requests"), (response) =>
  json(Schema.Array(RequestReport), response),
);

const print = (line: string) => Effect.sync(() => process.stdout.write(`${line}\n`));

const { positionals, values } = parseArgs({
  allowPositionals: true,
  options: {
    area: { type: "string", multiple: true, default: [] },
    wanted: { type: "string", default: "3" },
    timeout: { type: "string", default: "600" },
  },
});
const [command, first, second] = positionals;

const program = Effect.gen(function* () {
  if (command === "create" && first !== undefined) {
    const brief = yield* Effect.try({
      // A decoder drops a key it does not know, and a step would lose its timer to a typo such as `secs`.
      try: () =>
        Schema.decodeUnknownSync(Brief)(JSON.parse(readFileSync(first, "utf8")), { onExcessProperty: "error" }),
      catch: (cause) => new Failed({ message: `${first} is not a brief: ${String(cause)}` }),
    });
    const response = yield* call("POST", "/v1/admin/requests", {
      brief,
      requirements: values.area.map((value) => ({ kind: "area", value })),
      timeoutSeconds: Number(values.timeout),
      wanted: Number(values.wanted),
    });
    const created = yield* json(Schema.Struct({ id: Schema.String }), response);
    return yield* print(created.id);
  }
  if (command === "list") {
    for (const request of yield* reports) {
      yield* print(
        `${request.id}  ${request.status}  ${String(request.submissions.length)}/${String(request.wanted)} runs, ${String(request.skips)} skipped  ${request.brief.name}`,
      );
    }
    return;
  }
  if (command === "close" && first !== undefined) {
    yield* call("POST", `/v1/admin/requests/${first}/close`);
    return;
  }
  if (command === "pull" && first !== undefined && second !== undefined) {
    const request = (yield* reports).find((candidate) => candidate.id === first);
    if (request === undefined) {
      return yield* new Failed({ message: `No request has the id ${first}.` });
    }
    mkdirSync(second, { recursive: true });
    for (const submission of request.submissions) {
      const response = yield* call("GET", `/v1/admin/submissions/${submission.id}/capture`);
      const gzipped = yield* Effect.promise(() => response.arrayBuffer());
      const name = request.brief.name.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-");
      const file = join(second, `${name}-${submission.contributor.toLowerCase()}.jsonl`);
      yield* Effect.try(() => writeFileSync(file, gunzipSync(gzipped))).pipe(
        Effect.matchEffect({
          onSuccess: () => print(file),
          onFailure: () => print(`${file} skipped: run ${submission.id} is not a readable capture`),
        }),
      );
    }
    return;
  }
  if (command === "unknowns") {
    const unknowns = yield* Effect.flatMap(call("GET", "/v1/admin/unknowns"), (response) =>
      json(Schema.Array(Unknown), response),
    );
    for (const unknown of unknowns.toSorted((a, b) => b.count - a.count)) {
      yield* print(`${String(unknown.count)}x  ${unknown.kind}  ${unknown.signature}\n    ${unknown.sample}`);
    }
    return;
  }
  return yield* print(USAGE);
});

await Effect.runPromise(
  Effect.catch(program, (failed) =>
    Effect.sync(() => {
      process.stderr.write(`${failed.message}\n`);
      process.exitCode = 1;
    }),
  ),
);
