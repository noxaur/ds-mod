import { contribStore, itemStore, profileStore } from "../routes/_store.ts";
import { localCloudflare } from "./cloudflare.ts";
import { storeContract } from "./contract.ts";

storeContract("the D1 store", () =>
  Promise.resolve({
    stores: { contrib: contribStore, items: itemStore, profiles: profileStore },
    within: localCloudflare().within,
  }),
);
