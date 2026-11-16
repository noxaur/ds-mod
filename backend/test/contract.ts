import { Effect } from "effect";
import { describe, expect, test } from "vite-plus/test";

import type { RecordingRequest } from "../src/contrib/model.ts";
import type { Store, SubmissionRow } from "../src/contrib/store.ts";
import type { ItemSighting } from "../src/items/model.ts";
import type { ItemLimits, ItemStore, Observation } from "../src/items/store.ts";
import type { ProfileStore } from "../src/profiles/store.ts";

export interface Stores {
  readonly contrib: Store["Service"];
  readonly items: ItemStore["Service"];
  readonly profiles: ProfileStore["Service"];
}

/** A set of empty stores, and how to run a test body the way the stores need it. */
export interface Implementation {
  readonly stores: Stores;
  readonly within: <A>(body: () => Promise<A>) => Promise<A>;
}

const run = <A, E>(effect: Effect.Effect<A, E>) => Effect.runPromise(effect);

const request = (id: string, extra: Partial<RecordingRequest> = {}): RecordingRequest => ({
  id,
  status: "open",
  createdAt: "2026-01-01T00:00:00.000Z",
  brief: {
    name: `Brief ${id}`,
    steps: [
      { do: "Warp", note: "now" },
      { do: "Wait", seconds: 60 },
    ],
  },
  requirements: [{ kind: "area", value: "The End" }],
  timeoutSeconds: 300,
  wanted: 2,
  ...extra,
});

const submission = (id: string, requestId: string, userId: string, createdAt = "2026-01-01T00:00:00.000Z") =>
  ({ id, requestId, userId, modVersion: "1.0.0", bytes: 3, createdAt }) satisfies SubmissionRow;

const capture = (...bytes: number[]) => new Uint8Array([0x1f, 0x8b, ...bytes]);

const sample = (entry: string, text = "a"): ItemSighting => ({
  entry,
  material: "minecraft:iron_sword",
  name: [{ text }],
  lore: [],
  glint: true,
  data: {},
});

const seen = (entry: string, extra: Partial<Observation> = {}): Observation => ({
  entry,
  skinId: "",
  texture: null,
  glint: true,
  sample: null,
  ...extra,
});

const unknown = (signature: string, sampled = "first") => ({
  kind: "chat",
  signature,
  sample: sampled,
  modVersion: "1.0.0",
  firstSeen: "2026-01-01",
});

const appearances = (items: ItemStore["Service"], query: { entry?: string; after?: string; limit?: number } = {}) =>
  run(items.appearances({ limit: 200, ...query }));

const names = async (items: ItemStore["Service"]) => (await appearances(items)).items.map(({ entry }) => entry);

const roomy: ItemLimits = { maxRows: 1000, maxPlayerRows: 1000, maxSamples: 5 };

/**
 * What every store must do, whatever it keeps its rows in. The memory stores run it in every test run, and D1 runs the
 * same checks against the real migrations, so the two cannot disagree on a rule without a test failing.
 */
export const storeContract = (implementation: string, make: () => Promise<Implementation>) => {
  const check = (title: string, body: (stores: Stores) => Promise<void>) =>
    test(title, async () => {
      const { stores, within } = await make();
      await within(() => body(stores));
    });

  describe(`${implementation} contributions`, () => {
    check("a request comes back as it went in, oldest first, and an unknown id is null", async ({ contrib }) => {
      const late = request("late", { createdAt: "2026-03-01T00:00:00.000Z" });
      const early = request("early", { createdAt: "2026-02-01T00:00:00.000Z" });
      await run(contrib.insertRequest(late));
      await run(contrib.insertRequest(early));
      expect(await run(contrib.requests)).toEqual([early, late]);
      expect(await run(contrib.request("late"))).toEqual(late);
      expect(await run(contrib.request("nothing"))).toBeNull();
    });

    check("only an open request is filled, and a closed one stays closed", async ({ contrib }) => {
      for (const id of ["a", "b", "c"]) {
        await run(contrib.insertRequest(request(id)));
      }
      await run(contrib.fill("a"));
      await run(contrib.close("b"));
      await run(contrib.fill("b"));
      await run(contrib.close("c"));
      await run(contrib.fill("nothing"));
      const statuses = (await run(contrib.requests)).map(({ id, status }) => [id, status]);
      expect(statuses).toEqual([
        ["a", "filled"],
        ["b", "closed"],
        ["c", "closed"],
      ]);
    });

    check("a skip counts once, and doneBy lists the skips and runs of that player alone", async ({ contrib }) => {
      await run(contrib.insertSkip("r1", "ann"));
      await run(contrib.insertSkip("r1", "ann"));
      await run(contrib.insertSkip("r1", "bob"));
      await run(contrib.insertSubmission(submission("s1", "r2", "ann"), capture(1)));
      await run(contrib.insertSubmission(submission("s2", "r3", "bob"), capture(2)));
      expect(await run(contrib.skips)).toHaveLength(2);
      expect((await run(contrib.doneBy("ann"))).toSorted()).toEqual(["r1", "r2"]);
      expect(await run(contrib.doneBy("nobody"))).toEqual([]);
    });

    check("a player's second run is refused and leaves nothing behind", async ({ contrib }) => {
      expect(await run(contrib.insertSubmission(submission("s1", "r1", "ann"), capture(1)))).toBe(true);
      expect(await run(contrib.insertSubmission(submission("s2", "r1", "ann"), capture(2)))).toBe(false);
      expect(await run(contrib.capture("s1"))).toEqual(capture(1));
      expect(await run(contrib.capture("s2"))).toBeNull();
      expect(await run(contrib.insertSubmission(submission("s3", "r2", "ann"), capture(3)))).toBe(true);
      expect(await run(contrib.insertSubmission(submission("s4", "r1", "bob"), capture(4)))).toBe(true);
      expect(await run(contrib.capture("nothing"))).toBeNull();
    });

    check("two uploads of one player at once keep one", async ({ contrib }) => {
      const results = await Promise.all([
        run(contrib.insertSubmission(submission("s1", "r1", "ann"), capture(1))),
        run(contrib.insertSubmission(submission("s2", "r1", "ann"), capture(2))),
      ]);
      expect(results.toSorted()).toEqual([false, true]);
      const kept = await run(contrib.submissionsOf("r1"));
      expect(kept).toHaveLength(1);
      expect(await run(contrib.capture(kept[0]?.id ?? ""))).not.toBeNull();
    });

    check(
      "runs come oldest first, by id when the time is the same, whatever order they came in",
      async ({ contrib }) => {
        const rows = [
          submission("d", "r1", "p1", "2026-01-02T00:00:00.000Z"),
          submission("b", "r1", "p2", "2026-01-01T00:00:00.000Z"),
          submission("a", "r1", "p3", "2026-01-01T00:00:00.000Z"),
          submission("c", "r2", "p4", "2026-01-01T12:00:00.000Z"),
        ];
        for (const row of rows) {
          await run(contrib.insertSubmission(row, capture(1)));
        }
        expect((await run(contrib.submissions)).map(({ id }) => id)).toEqual(["a", "b", "c", "d"]);
        expect((await run(contrib.submissionsOf("r1"))).map(({ id }) => id)).toEqual(["a", "b", "d"]);
        expect(await run(contrib.submissionsOf("nothing"))).toEqual([]);
      },
    );

    check(
      "an unknown is counted again, keeps its first sample, and a new one stops at the cap",
      async ({ contrib }) => {
        await run(contrib.countUnknown(unknown("a"), 2));
        await run(contrib.countUnknown(unknown("a", "second"), 2));
        await run(contrib.countUnknown(unknown("b"), 2));
        await run(contrib.countUnknown(unknown("c"), 2));
        await run(contrib.countUnknown({ ...unknown("a"), kind: "other" }, 2));
        await run(contrib.countUnknown(unknown("a"), 2));
        const kept = (await run(contrib.unknowns)).toSorted((x, y) => x.signature.localeCompare(y.signature));
        expect(kept).toEqual([
          { ...unknown("a"), count: 3 },
          { ...unknown("b"), count: 1 },
        ]);
      },
    );
  });

  describe(`${implementation} items`, () => {
    check("a player counts once however often they sent it, and glint is what players saw", async ({ items }) => {
      for (let again = 0; again < 3; again++) {
        await run(items.record("ann", "1", [seen("a"), seen("a", { glint: false })], roomy));
      }
      await run(items.record("bob", "1", [seen("a")], roomy));
      await run(items.record("ann", "1", [seen("b")], roomy));
      await run(items.record("bob", "1", [seen("b")], roomy));
      await run(items.record("ann", "1", [seen("c", { glint: false })], roomy));
      await run(items.record("bob", "1", [seen("c", { glint: false })], roomy));
      const { items: found } = await appearances(items);
      expect(found).toMatchObject([
        { entry: "a", players: 2, glintPlayers: 2 },
        { entry: "b", players: 2, glintPlayers: 2 },
        { entry: "c", players: 2, glintPlayers: 0 },
      ]);
      const hints = await run(items.hints(["a", "b", "c", "nobody"]));
      expect([...hints].map(([entry, hint]) => [entry, hint.glint])).toEqual([
        ["a", "mixed"],
        ["b", "always"],
        ["c", "never"],
      ]);
    });

    check("nothing is agreed on until two players saw it", async ({ items }) => {
      await run(items.record("ann", "1", [seen("a")], roomy));
      expect((await run(items.hints(["a"]))).get("a")?.glint).toBeNull();
    });

    check(
      "a texture counts at two players, and of two for one skin the one more players saw wins",
      async ({ items }) => {
        const record = (who: string, observations: Observation[]) => run(items.record(who, "1", observations, roomy));
        await record("ann", [seen("h", { texture: "t1" }), seen("h", { skinId: "gold", texture: "g1" })]);
        await record("bob", [seen("h", { texture: "t1" }), seen("h", { skinId: "gold", texture: "g2" })]);
        await record("cat", [seen("h", { skinId: "gold", texture: "g2" })]);
        await record("dan", [seen("h", { skinId: "lone", texture: "x" })]);
        await record("ann", [seen("h", { texture: "t1" })]);
        expect((await run(items.hints(["h"]))).get("h")?.textures).toEqual(
          new Map([
            ["", "t1"],
            ["gold", "g2"],
          ]),
        );
      },
    );

    check("the last texture a player sent for a skin replaces their earlier one", async ({ items }) => {
      await run(items.record("ann", "1", [seen("h", { texture: "old" })], roomy));
      await run(items.record("ann", "1", [seen("h", { texture: "new" })], roomy));
      await run(items.record("bob", "1", [seen("h", { texture: "new" })], roomy));
      expect((await run(items.hints(["h"]))).get("h")?.textures).toEqual(new Map([["", "new"]]));
    });

    check("the rows stop at the cap, and an item already seen is still counted", async ({ items }) => {
      const limits = { maxRows: 2, maxPlayerRows: 10, maxSamples: 1 };
      await run(items.record("ann", "1", [seen("first")], limits));
      await run(items.record("bob", "1", [seen("second"), seen("third")], limits));
      await run(items.record("ann", "1", [{ ...seen("first"), glint: false }], limits));
      await run(items.record("bob", "1", [seen("first")], limits));
      expect((await appearances(items)).items).toMatchObject([
        { entry: "first", players: 1, glintPlayers: 1 },
        { entry: "second", players: 1 },
      ]);
    });

    check("an item the player already has is still counted at their cap", async ({ items }) => {
      const limits = { maxRows: 100, maxPlayerRows: 2, maxSamples: 1 };
      await run(items.record("ann", "1", [seen("a"), seen("b"), seen("c")], limits));
      await run(items.record("ann", "1", [seen("a", { glint: false }), seen("d")], limits));
      expect((await appearances(items)).items).toMatchObject([
        { entry: "a", players: 1 },
        { entry: "b", players: 1 },
      ]);
    });

    check("many accounts together stop at the table's cap", async ({ items }) => {
      const limits = { maxRows: 3, maxPlayerRows: 2, maxSamples: 1 };
      for (const who of ["ann", "bob", "cat"]) {
        await run(items.record(who, "1", [seen(`${who}1`), seen(`${who}2`)], limits));
      }
      expect(await names(items)).toEqual(["ann1", "ann2", "bob1"]);
    });

    check("textures stop at the same caps, and a refused item keeps no texture or sample", async ({ items }) => {
      const limits = { maxRows: 100, maxPlayerRows: 2, maxSamples: 5 };
      await run(
        items.record(
          "ann",
          "1",
          [
            seen("a", { texture: "ta", sample: sample("a") }),
            seen("b", { texture: "tb", sample: sample("b") }),
            seen("c", { texture: "tc", sample: sample("c") }),
          ],
          limits,
        ),
      );
      await run(items.record("bob", "1", [seen("a", { texture: "ta" })], limits));
      expect((await run(items.hints(["a", "c"]))).get("a")?.textures).toEqual(new Map([["", "ta"]]));
      expect((await run(items.hints(["c"]))).size).toBe(0);
      expect((await appearances(items, { entry: "c" })).items).toEqual([]);
    });

    check("an item keeps a few samples, one per player", async ({ items }) => {
      const limits = { maxRows: 100, maxPlayerRows: 100, maxSamples: 2 };
      for (const who of ["ann", "bob", "cat", "dan"]) {
        await run(items.record(who, "1", [seen("a", { sample: sample("a", who) })], limits));
      }
      await run(items.record("ann", "1", [seen("a", { sample: sample("a", "again") })], limits));
      const { items: found } = await appearances(items, { entry: "a" });
      expect(found).toMatchObject([{ players: 4 }]);
      expect(found[0]?.samples.map(({ name }) => name)).toEqual([[{ text: "ann" }], [{ text: "bob" }]]);
    });

    check(
      "a player's later sample does not replace their first, and a sample too big to keep is none",
      async ({ items }) => {
        await run(items.record("ann", "1", [seen("a", { sample: sample("a", "first") })], roomy));
        await run(items.record("ann", "1", [seen("a", { sample: sample("a", "second") })], roomy));
        await run(items.record("bob", "1", [seen("a")], roomy));
        const { items: found } = await appearances(items, { entry: "a" });
        expect(found[0]?.samples.map(({ name }) => name)).toEqual([[{ text: "first" }]]);
      },
    );

    check("the agent's view is one entry with its samples, or a page of entries without", async ({ items }) => {
      await run(items.record("ann", "1", [seen("a", { sample: sample("a") }), seen("b"), seen("c")], roomy));
      await run(items.record("bob", "1", [seen("a", { sample: sample("a") })], roomy));
      const one = await appearances(items, { entry: "a" });
      expect(one).toMatchObject({ items: [{ entry: "a", players: 2, samples: [{}, {}] }], next: null });
      expect((await appearances(items, { entry: "missing" })).items).toEqual([]);
      const first = await appearances(items, { limit: 2 });
      expect(first).toMatchObject({ items: [{ entry: "a", samples: [] }, { entry: "b" }], next: "b" });
      expect(await appearances(items, { limit: 2, after: "b" })).toMatchObject({ items: [{ entry: "c" }], next: null });
      expect(await appearances(items, { limit: 3 })).toMatchObject({ next: null });
      expect((await appearances(items, { after: "c" })).items).toEqual([]);
    });

    check("a page and a hints call hold more entries than one database statement may", async ({ items }) => {
      const entries = Array.from({ length: 250 }, (_, index) => `item${String(index).padStart(3, "0")}`);
      for (const who of ["ann", "bob"]) {
        for (let from = 0; from < entries.length; from += 50) {
          await run(
            items.record(
              who,
              "1",
              entries.slice(from, from + 50).map((entry) => seen(entry, { texture: "t" })),
              roomy,
            ),
          );
        }
      }
      const page = await appearances(items, { limit: 200 });
      expect(page.items.map(({ entry }) => entry)).toEqual(entries.slice(0, 200));
      expect(page.next).toBe(entries[199]);
      expect(page.items.every(({ players, textures }) => players === 2 && textures.length === 1)).toBe(true);
      const hints = await run(items.hints(entries));
      expect(hints.size).toBe(250);
      expect([...hints.values()].every((hint) => hint.glint === "always" && hint.textures.get("") === "t")).toBe(true);
    });
  });

  describe(`${implementation} profiles`, () => {
    check("a value comes back as it went in, and an unknown key is null", async ({ profiles }) => {
      await run(profiles.put("a", { name: "Ann", levels: [1, 2] }, 60));
      expect(await run(profiles.get("a"))).toEqual({ name: "Ann", levels: [1, 2] });
      expect(await run(profiles.get("nothing"))).toBeNull();
    });
  });
};
