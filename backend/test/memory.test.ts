import { storeContract } from "./contract.ts";
import { memoryStore } from "../src/contrib/store.ts";
import { memoryItemStore } from "../src/items/store.ts";
import { memoryProfileStore } from "../src/profiles/store.ts";

storeContract("the memory store", () =>
  Promise.resolve({
    stores: { contrib: memoryStore(), items: memoryItemStore(), profiles: memoryProfileStore() },
    within: (body) => body(),
  }),
);
