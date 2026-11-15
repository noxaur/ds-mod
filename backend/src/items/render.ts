import type { DrawnItem } from "./model.ts";
import type { ItemHints } from "./store.ts";

/** The entries upstream slots hold, as dragsim.co's player API sends them, to ask the store for hints about. */
export const entriesOf = (_items: readonly unknown[]): readonly string[] => [];

/** Draws dragsim.co's items as the game does. */
export interface Renderer {
  /** The item one upstream slot holds, as dragsim.co's player API sends it, or null when it holds none. */
  readonly render: (item: unknown) => DrawnItem | null;
}

/** A renderer for dragsim.co's `catalog.json` and the hints players agree on. Make one per request. */
export const makeRenderer = (_catalog: unknown, _hints: ReadonlyMap<string, ItemHints>): Renderer => ({
  render: () => null,
});
