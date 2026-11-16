import { contribStore, itemStore, profileStore } from "../routes/_store.ts";
import { localCloudflare } from "./cloudflare.ts";
import { storeContract } from "./contract.ts";

// The stores of `routes/_store.ts` over a real SQLite with the real migrations, checked by the memory store's suite.
storeContract("the D1 store", () =>
  Promise.resolve({
    stores: { contrib: contribStore, items: itemStore, profiles: profileStore },
    within: localCloudflare().within,
  }),
);
