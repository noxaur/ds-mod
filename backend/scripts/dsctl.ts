import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { parseArgs } from "node:util";
import { gunzipSync } from "node:zlib";

import { Data, Effect, Schema } from "effect";
import { FetchHttpClient, HttpClient, HttpClientRequest } from "effect/http";
import { HttpApiClient } from "effect/http-api";

import { Api } from "../src/api.ts";
import { RequestSpec } from "../src/contrib/model.ts";

const USAGE = `dsctl asks players for captures and reads them back.

  dsctl create <brief.json> [--area <name>]... [--wanted 3] [--timeout 600]
      Publishes a brief, the same file runClient -Pbrief takes, and prints the request's id. Players in each --area
      are asked, until --wanted of them recorded it. A run longer than --timeout seconds is thrown away.
  dsctl list [--json]        Every request with its runs and how many players skipped it.
  dsctl close <request id>   Stops offering a request.
  dsctl pull <request id> <directory>
      Writes each run as <directory>/<brief name>-<letter>.jsonl, ready for cutFixture, and prints each path.
  dsctl unknowns [--json]    What the mod saw and had no handling for, most seen first.

DSMOD_BACKEND is the backend's address (http://127.0.0.1:8787 by default) and DSMOD_ADMIN_TOKEN its admin token.
A failure goes to stderr with exit code 1.`;

class Failed extends Data.TaggedError("Failed")<{ readonly message: string }> {}
class Unreadable extends Data.TaggedError("Unreadable") {}

/** What dsctl runs against: the backend's address, the admin token, and the fetch that reaches it. */
export interface Target {
  readonly backend: string;
  readonly token: string;
  readonly fetch: typeof globalThis.fetch;
}

/** Runs one dsctl command and gives back the lines it prints. */
export const dsctl = (args: readonly string[], target: Target) =>
  Effect.gen(function* () {
    const { positionals, values } = yield* Effect.try({
      try: () =>
        parseArgs({
          args: [...args],
          allowPositionals: true,
          options: {
            area: { type: "string", multiple: true, default: [] },
            wanted: { type: "string", default: "3" },
            timeout: { type: "string", default: "600" },
            json: { type: "boolean", default: false },
          },
        }),
      catch: (cause) => new Failed({ message: `${String(cause)}\n\n${USAGE}` }),
    });
    const [command, first, second] = positionals;
    const client = yield* HttpApiClient.make(Api, {
      baseUrl: target.backend,
      transformClient: HttpClient.mapRequest(HttpClientRequest.bearerToken(target.token)),
    });

    if (command === "create" && first !== undefined) {
      const spec = yield* Effect.try(() => JSON.parse(readFileSync(first, "utf8")) as unknown).pipe(
        // The API refuses a key it does not know too, but its answer cannot say which.
        Effect.flatMap((brief) =>
          Schema.decodeUnknownEffect(RequestSpec)(
            {
              brief,
              requirements: values.area.map((value) => ({ kind: "area", value })),
              timeoutSeconds: Number(values.timeout),
              wanted: Number(values.wanted),
            },
            { onExcessProperty: "error" },
          ),
        ),
        Effect.mapError((error) => new Failed({ message: `${first} cannot be published: ${error.message}` })),
      );
      return [(yield* client.admin.create({ payload: spec })).id];
    }
    if (command === "list" && first === undefined) {
      const requests = yield* client.admin.requests();
      return values.json
        ? [JSON.stringify(requests)]
        : requests.map(
            (request) =>
              `${request.id}  ${request.status}  ${String(request.submissions.length)}/${String(request.wanted)} runs, ${String(request.skips)} skipped  ${request.brief.name}`,
          );
    }
    if (command === "close" && first !== undefined) {
      yield* client.admin.close({ params: { id: first } });
      return [];
    }
    if (command === "pull" && first !== undefined && second !== undefined) {
      const request = (yield* client.admin.requests()).find((candidate) => candidate.id === first);
      if (request === undefined) {
        return yield* new Failed({ message: `No request has the id ${first}.` });
      }
      yield* Effect.try({
        try: () => mkdirSync(second, { recursive: true }),
        catch: (cause) => new Failed({ message: `Could not make ${second}: ${String(cause)}` }),
      });
      const name = request.brief.name.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-");
      return yield* Effect.forEach(request.submissions, (submission) => {
        const file = join(second, `${name}-${submission.contributor.toLowerCase()}.jsonl`);
        const skipped = `${file} skipped: run ${submission.id} has no readable capture`;
        return client.admin.capture({ params: { id: submission.id } }).pipe(
          Effect.flatMap((gzipped) => Effect.try({ try: () => gunzipSync(gzipped), catch: () => new Unreadable() })),
          Effect.flatMap((capture) =>
            Effect.try({
              try: () => writeFileSync(file, capture),
              catch: (cause) => new Failed({ message: `Could not write ${file}: ${String(cause)}` }),
            }),
          ),
          Effect.as(file),
          // One lost or broken run should not cost the agent the others.
          Effect.catchTags({ NotFound: () => Effect.succeed(skipped), Unreadable: () => Effect.succeed(skipped) }),
        );
      });
    }
    if (command === "unknowns" && first === undefined) {
      const unknowns = (yield* client.admin.unknowns()).toSorted((a, b) => b.count - a.count);
      return values.json
        ? [JSON.stringify(unknowns)]
        : unknowns.map(
            (unknown) => `${String(unknown.count)}x  ${unknown.kind}  ${unknown.signature}\n    ${unknown.sample}`,
          );
    }
    return yield* new Failed({ message: USAGE });
  }).pipe(
    Effect.catchTags({
      Unauthorized: () =>
        Effect.fail(new Failed({ message: "The backend refused the admin token. Set DSMOD_ADMIN_TOKEN." })),
      NotFound: () => Effect.fail(new Failed({ message: `Nothing on the backend has the id ${args[1] ?? ""}.` })),
      HttpClientError: (error) =>
        Effect.fail(new Failed({ message: `Could not reach ${target.backend}: ${error.message}` })),
      SchemaError: (error) =>
        Effect.fail(new Failed({ message: `The backend answered something dsctl cannot read: ${error.message}` })),
    }),
    Effect.provide(FetchHttpClient.layer),
    Effect.provideService(FetchHttpClient.Fetch, target.fetch),
  );

if (import.meta.main) {
  const target = {
    backend: process.env["DSMOD_BACKEND"] ?? "http://127.0.0.1:8787",
    token: process.env["DSMOD_ADMIN_TOKEN"] ?? "",
    fetch: globalThis.fetch,
  };
  await Effect.runPromise(
    dsctl(process.argv.slice(2), target).pipe(
      Effect.match({
        onSuccess: (lines) => lines.forEach((line) => process.stdout.write(`${line}\n`)),
        onFailure: (failed) => {
          process.stderr.write(`${failed.message}\n`);
          process.exitCode = 1;
        },
      }),
    ),
  );
}
