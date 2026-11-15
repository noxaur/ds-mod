import { Context, Effect } from "effect";

import type { ItemAppearance, ItemSighting } from "./model.ts";

/**
 * What the players who agree on an item saw, and so what the renderer may trust. Each needs enough different players,
 * because one client can send anything. `glint` is null when too few players saw the item.
 */
export interface ItemHints {
  readonly glint: "always" | "never" | "mixed" | null;
  /** Skull textures by `skinId`, `""` for the item's own, each on `textures.minecraft.net`. */
  readonly textures: ReadonlyMap<string, string>;
}

/** The sightings items have. A rule about them lives in `items.ts`, and this only reads and writes. */
export class ItemStore extends Context.Service<
  ItemStore,
  {
    readonly insertSightings: (
      userId: string,
      modVersion: string,
      sightings: readonly ItemSighting[],
    ) => Effect.Effect<void>;
    readonly appearances: Effect.Effect<readonly ItemAppearance[]>;
    /** What enough players agree on, for the entries asked about. An entry nobody saw is left out. */
    readonly hints: (entries: readonly string[]) => Effect.Effect<ReadonlyMap<string, ItemHints>>;
  }
>()("dsmod/items/ItemStore") {}

/** A store that forgets everything when the process ends, for the tests. */
export const memoryItemStore = (): ItemStore["Service"] => ({
  insertSightings: () => Effect.die("not built yet"),
  appearances: Effect.die("not built yet"),
  hints: () => Effect.succeed(new Map()),
});
