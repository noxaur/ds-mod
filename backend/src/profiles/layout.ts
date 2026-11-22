import type { Line } from "../items/model.ts";
import type { Sections } from "./wire.ts";

export interface Layout {
  readonly id: string;
  readonly title: Line;
  readonly columns: number;
  readonly slots: number;
  // @keep The items of the container, or for `pets` the pets as the server sends them.
  readonly cells: readonly unknown[];
}

export const PETS_ID = "pets";

const COLUMNS = 9;

/* @keep What dragsim.co's data shows of where items sit, from three players' profiles:
 * - `inventory` has keys 0 to 44. 0 to 8 are the hotbar, 9 to 35 the rows above it, and 41 to 44 the worn helmet,
 *   chestplate, leggings and boots. 36 to 40 were empty on all three, so what they are is unknown.
 * - `ender_chest` has keys 0 to 26 and `storage` has 30 pages of keys 0 to 44. `accessory_bag` has no page and went to
 *   key 40. The bag grows with the data up to ten rows, and a key past that is not a slot.
 * - A key outside these places is never dropped. Each container lists the items it has no place for in an `:other`
 *   container beside it, packed in key order, because where such an item sits is unknown.
 * - `wardrobe.slots` has keys 0 to 8, each a helmet, chestplate, leggings and boots. `equipped` names the slot that is
 *   worn, which has no entry because its items are in the inventory's armor keys.
 */
const HELMET = 41;
const ARMOR_SLOTS = 4;
const HOTBAR_FIRST = 0;
const HOTBAR_SLOTS = 9;
const MAIN_FIRST = 9;
const MAIN_SLOTS = 27;
const ENDER_CHEST_SLOTS = 27;
const STORAGE_PAGE_SLOTS = 45;
// @keep Ten rows. Captures reached key 40, and a key past this would make the grid follow it.
const ACCESSORY_BAG_SLOTS = COLUMNS * 10;
const WARDROBE_PAGE = 9;

const plain = (text: string): Line => [{ text }];

const SLOT_KEY = /^(0|[1-9]\d*)$/u;

const isItem = (value: unknown): boolean =>
  typeof value === "object" && value !== null && Object.keys(value).length > 0;

const strays = (entries: Readonly<Record<string, unknown>> | null | undefined, placed: (key: number) => boolean) =>
  Object.entries(entries ?? {})
    .filter(([key, value]) => isItem(value) && !(SLOT_KEY.test(key) && placed(Number(key))))
    .toSorted(([a], [b]) => (SLOT_KEY.test(a) ? Number(a) : Infinity) - (SLOT_KEY.test(b) ? Number(b) : Infinity))
    .map(([, value]) => value);

const packed = (items: readonly unknown[]): readonly unknown[] => [
  ...items,
  ...Array.from({ length: (COLUMNS - (items.length % COLUMNS)) % COLUMNS }, () => null),
];

const others = (id: string, title: string, items: readonly unknown[]): readonly Layout[] =>
  items.length === 0 ? [] : [grid(id, title, packed(items))];

const cell = (items: Readonly<Record<string, unknown>> | null | undefined, key: number): unknown =>
  items?.[String(key)] ?? null;

const lastKey = (keys: readonly string[]): number =>
  Math.max(-1, ...keys.filter((key) => SLOT_KEY.test(key)).map(Number));

const rows = (
  items: Readonly<Record<string, unknown>> | null | undefined,
  minimum: number,
  maximum: number,
): readonly unknown[] => {
  const last = lastKey(Object.keys(items ?? {}).filter((key) => !SLOT_KEY.test(key) || Number(key) < maximum));
  const slots = Math.max(minimum, Math.ceil((last + 1) / COLUMNS) * COLUMNS);
  return Array.from({ length: Math.min(slots, maximum) }, (_, key) => cell(items, key));
};

const grid = (id: string, title: string, cells: readonly unknown[]): Layout => ({
  id,
  title: plain(title),
  columns: COLUMNS,
  slots: cells.length,
  cells,
});

// @keep The API does not say what the player holds, so `held` is the first hotbar slot.
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
  const pages = Array.from({ length: Math.max(1, Math.ceil((last + 1) / WARDROBE_PAGE)) }, (_page, page) =>
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
  const unplaced = Object.entries(slots).flatMap(([key, parts]) =>
    Object.entries(parts)
      .filter(([part]) => !SLOT_KEY.test(key) || !(WARDROBE_PARTS as readonly string[]).includes(part))
      .map(([, value]) => ({ key, value })),
  );
  return [
    ...pages,
    ...others(
      "wardrobe:other",
      "Wardrobe (other)",
      unplaced.filter(({ value }) => isItem(value)).map(({ value }) => value),
    ),
  ];
};

const isInventorySlot = (key: number): boolean =>
  key < HOTBAR_SLOTS + MAIN_SLOTS || (key >= HELMET && key < HELMET + ARMOR_SLOTS);

export const layoutsOf = (sections: Sections): readonly Layout[] => {
  const storage = sections.storage ?? {};
  const pageKeys = Object.keys(storage);
  const pages = pageKeys.filter((key) => SLOT_KEY.test(key)).toSorted((a, b) => Number(a) - Number(b));
  const pets = sections.pets ?? [];
  return [
    // @keep The game shows the hotbar under the rows, though it holds the first keys.
    grid("inventory", "Inventory", [
      ...Array.from({ length: MAIN_SLOTS }, (_, key) => cell(sections.inventory, MAIN_FIRST + key)),
      ...Array.from({ length: HOTBAR_SLOTS }, (_, key) => cell(sections.inventory, HOTBAR_FIRST + key)),
    ]),
    ...others("inventory:other", "Inventory (other slots)", strays(sections.inventory, isInventorySlot)),
    grid("ender_chest", "Ender Chest", rows(sections.ender_chest, ENDER_CHEST_SLOTS, ENDER_CHEST_SLOTS)),
    ...others(
      "ender_chest:other",
      "Ender Chest (other)",
      strays(sections.ender_chest, (key) => key < ENDER_CHEST_SLOTS),
    ),
    ...pages.map((key) =>
      grid(
        `storage:${key}`,
        `Storage ${String(Number(key) + 1)}`,
        rows(storage[key], STORAGE_PAGE_SLOTS, STORAGE_PAGE_SLOTS),
      ),
    ),
    ...others("storage:other", "Storage (other)", [
      ...pages.flatMap((key) => strays(storage[key], (slot) => slot < STORAGE_PAGE_SLOTS)),
      ...pageKeys.filter((key) => !SLOT_KEY.test(key)).flatMap((key) => strays(storage[key], () => false)),
    ]),
    ...wardrobePages(sections.wardrobe ?? {}),
    grid("accessories", "Accessory Bag", rows(sections.accessory_bag, COLUMNS, ACCESSORY_BAG_SLOTS)),
    ...others(
      "accessories:other",
      "Accessory Bag (other)",
      strays(sections.accessory_bag, (key) => key < ACCESSORY_BAG_SLOTS),
    ),
    ...(pets.length === 0 ? [] : [grid(PETS_ID, "Pets", packed(pets))]),
  ];
};
