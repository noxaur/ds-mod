import type { DrawnItem } from "./model.ts";
import type { ItemHints } from "./store.ts";

/** Draws dragsim.co's items as the game does, from the catalog it was made with. */
export interface Renderer {
  /** The item one upstream slot holds, as dragsim.co's player API sends it, or null when it holds none. */
  readonly render: (item: unknown) => DrawnItem | null;
  /** The entries [items] hold, to ask the store for hints about before rendering them. */
  readonly entries: (items: readonly unknown[]) => readonly string[];
}

/** A renderer for dragsim.co's `catalog.json`, decoded once, and the hints players agree on. */
export const makeRenderer = (_catalog: unknown, _hints: ReadonlyMap<string, ItemHints>): Renderer => ({
  render: () => null,
  entries: () => [],
});
