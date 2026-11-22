import { readFileSync } from "node:fs";
import { join } from "node:path";
import { DatabaseSync, type SQLInputValue } from "node:sqlite";

import { Effect, Exit } from "effect";
import { withRuntimeEnv } from "void/_env";

// @keep D1 refuses a statement with more bound values than this.
const MAX_BOUND_VALUES = 100;

type Row = Record<string, unknown>;

/** @keep D1 over Node's SQLite runs the real SQL. It does not model Cloudflare's network, read replicas or timing. */
class Statement {
  constructor(
    private readonly sqlite: DatabaseSync,
    private readonly text: string,
    private readonly values: readonly SQLInputValue[] = [],
  ) {}

  bind(...values: SQLInputValue[]) {
    if (values.length > MAX_BOUND_VALUES) {
      throw new Error(`D1_ERROR: too many SQL variables (${String(values.length)} bound, at most ${MAX_BOUND_VALUES})`);
    }
    return new Statement(this.sqlite, this.text, values);
  }

  rows(): Row[] {
    return this.sqlite.prepare(this.text).all(...this.values);
  }

  all() {
    return Promise.resolve({ success: true, results: this.rows() });
  }

  run() {
    this.sqlite.prepare(this.text).run(...this.values);
    return Promise.resolve({ success: true, results: [] });
  }

  raw() {
    const statement = this.sqlite.prepare(this.text);
    statement.setReturnArrays(true);
    return Promise.resolve(statement.all(...this.values));
  }
}

class D1 {
  constructor(private readonly sqlite: DatabaseSync) {}

  prepare(text: string) {
    return new Statement(this.sqlite, text);
  }

  batch(statements: Statement[]) {
    return Effect.runPromise(
      Effect.acquireUseRelease(
        Effect.sync(() => this.sqlite.exec("BEGIN")),
        () =>
          Effect.try({
            try: () => statements.map((statement) => ({ success: true, results: statement.rows() })),
            catch: (error) => error,
          }),
        (_, exit) => Effect.sync(() => this.sqlite.exec(Exit.isSuccess(exit) ? "COMMIT" : "ROLLBACK")),
      ),
    );
  }

  withSession() {
    return this;
  }
}

const migrate = (sqlite: DatabaseSync) => {
  const directory = join(import.meta.dirname, "..", "db", "migrations");
  const journal: { entries: { tag: string }[] } = JSON.parse(
    readFileSync(join(directory, "meta", "_journal.json"), "utf8"),
  );
  for (const { tag } of journal.entries) {
    for (const statement of readFileSync(join(directory, `${tag}.sql`), "utf8").split("--> statement-breakpoint")) {
      sqlite.exec(statement);
    }
  }
};

const r2 = () => {
  const objects = new Map<string, Uint8Array>();
  return {
    put: (key: string, value: Uint8Array) => Promise.resolve(void objects.set(key, value)),
    delete: (key: string) => Promise.resolve(void objects.delete(key)),
    get: (key: string) => {
      const value = objects.get(key);
      return Promise.resolve(value === undefined ? null : { arrayBuffer: () => Promise.resolve(value.slice().buffer) });
    },
  };
};

const kvNamespace = (now: () => number) => {
  const values = new Map<string, { value: string; until: number }>();
  return {
    get: (key: string) => {
      const kept = values.get(key);
      return Promise.resolve(kept !== undefined && now() < kept.until ? kept.value : null);
    },
    put: (key: string, value: string, options: { expirationTtl?: number } = {}) =>
      Promise.resolve(void values.set(key, { value, until: now() + (options.expirationTtl ?? Infinity) * 1000 })),
  };
};

export const localCloudflare = (now: () => number = Date.now) => {
  const sqlite = new DatabaseSync(":memory:");
  migrate(sqlite);
  const env = { DB: new D1(sqlite), STORAGE: r2(), KV: kvNamespace(now) };
  return <A>(body: () => Promise<A>): Promise<A> => withRuntimeEnv(env, body);
};
