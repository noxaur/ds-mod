import type { Line } from "../items/model.ts";
import type { Sections } from "./wire.ts";

/** A container as the game shows it: raw upstream items in reading order, null where a slot is empty. */
export interface Layout {
  readonly id: string;
  readonly title: Line;
  readonly columns: number;
  readonly slots: number;
  readonly cells: readonly unknown[];
}

const COLUMNS = 9;

/*
 * What dragsim.co's data shows of where items sit, from three players' profiles:
 * - `inventory` has keys 0 to 44. 0 to 8 are the hotbar, 9 to 35 the rows above it, and 41 to 44 the worn helmet,
 *   chestplate, leggings and boots. 36 to 40 were empty on all three, so what they are is unknown.
 * - `ender_chest` has keys 0 to 26 and `storage` has 30 pages of keys 0 to 44. `accessory_bag` has no page and went to
 *   key 40, so its size is the data's.
 * - `wardrobe.slots` has keys 0 to 8, each a helmet, chestplate, leggings and boots. `equipped` names the slot that is
 *   worn, which has no entry because its items are in the inventory's armor keys.
 */
const HELMET = 41;
const HOTBAR_FIRST = 0;
const HOTBAR_SLOTS = 9;
const MAIN_FIRST = 9;
const MAIN_SLOTS = 27;
const ENDER_CHEST_SLOTS = 27;
const STORAGE_PAGE_SLOTS = 45;
const WARDROBE_PAGE = 9;

const plain = (text: string): Line => [{ text }];

const cell = (items: Readonly<Record<string, unknown>> | null | undefined, key: number): unknown =>
  items?.[String(key)] ?? null;

/** The highest slot number among `keys`, or -1 when there is none. */
const lastKey = (keys: readonly string[]): number =>
  Math.max(-1, ...keys.map(Number).filter((key) => Number.isInteger(key)));

/** Slots numbered from 0, in as many full rows as the items need, and at least `minimum` slots. */
const rows = (items: Readonly<Record<string, unknown>> | null | undefined, minimum: number): readonly unknown[] => {
  const last = lastKey(Object.keys(items ?? {}));
  const slots = Math.max(minimum, Math.ceil((last + 1) / COLUMNS) * COLUMNS);
  return Array.from({ length: slots }, (_, key) => cell(items, key));
};

const grid = (id: string, title: string, cells: readonly unknown[]): Layout => ({
  id,
  title: plain(title),
  columns: COLUMNS,
  slots: cells.length,
  cells,
});

/** What the player wears, helmet first, and the item in the first hotbar slot, which is a guess at what they hold. */
export const equipmentCells = (inventory: Sections["inventory"]) => ({
  helmet: cell(inventory, HELMET),
  chestplate: cell(inventory, HELMET + 1),
  leggings: cell(inventory, HELMET + 2),
  boots: cell(inventory, HELMET + 3),
  held: cell(inventory, HOTBAR_FIRST),
});

const WARDROBE_PARTS = ["helmet", "chestplate", "leggings", "boots"] as const;

const wardrobePages = (wardrobe: NonNullable<Sections["wardrobe"]>): readonly Layout[] => {
  const slots = wardrobe.slots ?? {};
  const last = lastKey(Object.keys(slots));
  return Array.from({ length: Math.max(1, Math.ceil((last + 1) / WARDROBE_PAGE)) }, (_page, page) =>
    // A row for each part, and a column for each wardrobe slot, as the game lays the wardrobe out.
    grid(
      `wardrobe:${String(page)}`,
      `Wardrobe ${String(page + 1)}`,
      WARDROBE_PARTS.flatMap((part) =>
        Array.from(
          { length: WARDROBE_PAGE },
          (_, column) => slots[String(page * WARDROBE_PAGE + column)]?.[part] ?? null,
        ),
      ),
    ),
  );
};

/** Every container of a profile, in the order a client lists them. */
export const layoutsOf = (sections: Sections): readonly Layout[] => {
  const storage = sections.storage ?? {};
  const pages = Object.keys(storage)
    .filter((key) => /^\d+$/u.test(key))
    .toSorted((a, b) => Number(a) - Number(b));
  return [
    // The game shows the hotbar under the rows, though it holds the first keys.
    grid("inventory", "Inventory", [
      ...Array.from({ length: MAIN_SLOTS }, (_, key) => cell(sections.inventory, MAIN_FIRST + key)),
      ...Array.from({ length: HOTBAR_SLOTS }, (_, key) => cell(sections.inventory, HOTBAR_FIRST + key)),
    ]),
    grid("ender_chest", "Ender Chest", rows(sections.ender_chest, ENDER_CHEST_SLOTS)),
    ...pages.map((key) =>
      grid(`storage:${key}`, `Storage ${String(Number(key) + 1)}`, rows(storage[key], STORAGE_PAGE_SLOTS)),
    ),
    ...wardrobePages(sections.wardrobe ?? {}),
    grid("accessories", "Accessory Bag", rows(sections.accessory_bag, COLUMNS)),
  ];
};
