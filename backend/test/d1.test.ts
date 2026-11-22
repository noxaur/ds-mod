import { Effect } from "effect";
import { expect, test } from "vite-plus/test";

import { contribStore, itemStore, profileStore } from "../routes/_store.ts";
import { localCloudflare } from "./cloudflare.ts";
import { storeContract } from "./contract.ts";
import { sighting } from "./harness.ts";

storeContract("the D1 store", () =>
  Promise.resolve({
    stores: { contrib: contribStore, items: itemStore, profiles: profileStore },
    within: localCloudflare().within,
  }),
);

const sample = (text: string) => JSON.stringify(sighting("scylla", { name: [{ text }] }));

test("a sample kept before a bound was tightened is left out, and its entry still reads", async () => {
  const { sqlite, within } = localCloudflare();
  for (const [user, text] of [
    ["ann", "x".repeat(2000)],
    ["bob", "Scylla"],
  ] as const) {
    sqlite.prepare("INSERT INTO item_seen VALUES ('scylla', ?, 1, 0, '1.0.0')").run(user);
    sqlite.prepare("INSERT INTO item_samples VALUES ('scylla', ?, ?)").run(user, sample(text));
  }
  const { items } = await within(() => Effect.runPromise(itemStore.appearances({ entry: "scylla", limit: 1 })));
  expect(items).toMatchObject([{ entry: "scylla", players: 2, samples: [{ name: [{ text: "Scylla" }] }] }]);
});
