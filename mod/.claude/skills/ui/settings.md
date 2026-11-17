# Settings

ds-mod's settings screen draws every setting from one tree, `SettingsTree` in `core/config`. A feature declares its own settings in its class, and the tree places the feature once, in its section and group. A setting that belongs to no feature, such as the theme, is a field of `DsModConfig` bound by one line in the tree. Either way a setting is one line with a kind of row, and the screen gets its control, search, reset and keyboard from the kind. Every setting is edited in the screen itself, and only an editor that needs the game behind it, such as Move HUD, opens a screen of its own. `/dsmod` opens the screen, and `/dskit rows`, `/dskit lists` and `/dskit panels` show every kind in the gallery.

## Add a setting

A feature's setting is one line in the feature's class, as `val inFight = switch("beamInFight", "During the Fight", "Keep the beam while the Protector is up.", default = false)`, with a kind from the table below. Its first argument is its key in the player's config file, under the feature's `Store`, so never rename one that shipped: a key that has to change names the old one as `formerly`. Its default is written there and nowhere else. The feature reads it as `inFight.value`, and the edge as `DsModClient.feature<ProtectorSpawnBeam>().inFight.value`. Write no drawing or input code. A new class of values needs `@Serializable`.

A setting of no feature is a field in its class of `DsModConfig` with a default, and one line in `SettingsTree` in its section and group. The field's name is its key, and its default is read from a fresh `DsModConfig()`, so write it nowhere else.

A saved value that no row should edit is `stored("inventoryStyle", SlotStyle())` in its feature, for a row that reads and writes several, or goes on `SettingsTree.STORED` with the reason when it is a field. `STORED` is empty, because every field the player sets has a row: a list of things the player made is an Items row, and a canvas is a Panel row. A row that edits several slots at once names the rest with `Bind.spanning`.

| Kind | Declare it with | For |
| --- | --- | --- |
| Switch | `switch("beamInFight", "During the Fight", "Keep the beam while the Protector is up.", default = false)` | On or off |
| Switch with settings | `switch("hotbarMarks", "Hotbar", "Mark your hotbar as well.", default = true) { switch(..); pick(..) }` | Rows of the feature's that open under one of its switches, declared with `Rows` calls over `Bind`s, and dim while it is off |
| Number | `number("cameraDistance", "Distance", "Set how far the camera sits.", 4f, 1f..50f, step = 0.5f, unit = "°")` | A number in a range, stepped from its minimum |
| Choice | `choice("lootAlertTier", "Alert Tier", "Pick the lowest tier that alerts you.", LootTier.EPIC)` | One value of an enum, labeled by its `toString` |
| Pick | `pick("rarityPreset", "Style", "Pick a look for the marks.", RarityPreset.GILDED) { Look.Slots(it.style) }`, or `pick(hud::theme, ..) { Look.Swatch(it) }` in the tree | A value chosen by how it looks |
| Keys | `keys("freeLookKey", "Key", "Pick the key for Free Look.", 342)`, or `keys(g::settingsKey, ..)` in the tree | One key or mouse button, as a GLFW code or `KeyBind.NONE` |
| Span | `span("beamSections", "Beam Timing", "Pick when the beam shows.", listOf(Section(175, 200)), max = 200)` | Stretches of a scale, by `Sections`' rules |
| Fold | `fold("customLook", "Custom Look", "Build your own look.", border, fill, floor)` | Rows the player rarely needs, under a bar. The rows are declared before it, and the fold takes them from their place to its own |
| Action | `action("resetAnimations", "Reset Animations", "Put every Animations setting back.", button = "Reset") { .. }`, or `action(Opens.HUD_EDITOR, "Move HUD", "..", button = "Move", id = "moveHud")` in the tree | A button: a reset of the rows around it, or a screen of the edge's that `DsModClient` registers for its `Opens`, only for an editor that needs the game behind it or a screen that is not a setting, such as Contribute's requests |
| Items | `rows { items(binds, "Binds", "Name each bind, pick its keys and choose where it works.", CommandBinds) }` | A list of things the player made, in groups, each a line of cells with details under it, by an `ItemKind` in `core`. Its value is a `Grouped`, bound across `stored(..)` slots with `Bind.spanning` |
| Panel | `rows { panel(tabs.bind, "Tabs", "Press a place to add a tab there, and drag a tab to move it.", Canvas.MENU_TABS, id = "tabs") { .. } }` | A canvas the player edits by pointing, such as the HUD layout or the menu tabs, over a `stored(..)` slot, drawn by the `Editor` that `DsModClient` registers for its `Canvas` |
| Order | `order("equipmentPieces", "Pieces", "Pick the pieces to show, and drag them into order.", EquipmentPiece.entries)` | Which values of an enum show and in what order, as a `List` |
| Preview | `preview("modelPreview", "Preview", "Drag your model to turn it, and see the settings below on it.", Canvas.PLAYER_MODEL)` | A picture of what the rows around it do, which holds no value, drawn by the `Editor` that `DsModClient` registers for its `Canvas` |

The tree places a feature as a group of its own, `group(FreeLook(c))` or `group(EtherwarpOutline(c), "Etherwarp")` when the heading is not the title, with its switch beside the heading and its rows under it. A feature among others in a group is a row, `feature(DragonTimer(c))`, whose rows open under its switch. A row that needs a switch elsewhere names it with `dependsOn`. Either one dims the row while the switch is off.

A feature's switch row has the feature's id, and every other row's id is its key, or the id a fold, action or preview is given. Never change an id once it ships, because a link to the setting and a favorite name it.

A row bound to part of a value, such as one part of a rarity style, takes a `Bind` with a `get` and a `set` and names the part it reaches, as `Inventory.inventoryStyle.border`. Its `set` does what the existing editor does to the whole, such as switching the style to Custom. `ItemRarity` is the example.

## Wording

- A name is Title Case, three words or fewer, and says what the setting is: "Slot Lock", "F5 Views".
- A description is one sentence that starts with a verb and ends with a period, in at most `Setting.DESCRIPTION_MAX` characters. A row shows two lines of it and the rest in a tooltip, so put what matters first.
- A choice's values read in Title Case, from the enum's `toString`.

## Add a look

A pick shows each value as a `Look`, a case in `core/ui/Look.kt` that `Looks` in `edge/ui` draws 18 tall. Add the case, its width and its drawing in `Looks`, built from the kit's shapes, and use it in a `pick`. `KitTest` fails until the gallery's page of rows has a pick that shows it, so add one there.

## Add a list or a canvas

A list of things the player made is an Items row, so it needs no kind of its own. Write an `ItemKind` in `core` beside the thing's rules: its noun, its cells (`Cell.Check`, `Text`, `Keys`, `Pick`, `Count`), the cells its details hold, its groups, which items clash, and how a group is written to the clipboard. `ItemsEditor` in `edge/ui` draws it and gives it every convention of the list in `controls.md`.

A canvas, such as a layout the player drags, is a Panel row. Add its entry to `Canvas`, an `Editor` in `edge` that lays it out on change and draws it from the kit, and its registration in `DsModClient`. The row gives it its header, marker, dimming and search. The editor takes the keys only after the player enters it, and gives them back on Escape. Show it on the gallery's page of panels.

## Add a kind

Only when no kind fits, and ask the user first, since every kind is one more thing a player learns.

1. Add the class to `Settings.kt` in `core/config`, under `Valued` when it holds a value, with its rules: stepping, clamping, its summary, its default's label, its search labels, and its value as the narrator says it in `spoken`. Test each rule in `SettingsTest`.
2. Add its entry to `Kind`, with the word the narrator calls it, and its builder to `Rows`.
3. Give it a face in `SettingRows` in `edge/ui`: where its control sits in `measure`, what it shows in `refresh`, how it draws in `face`, and what a press, a drag, the wheel and each key do. Draw with the kit and text by role.
4. Show it idle, changed and dimmed on the gallery's page of rows, or of lists or panels for a kind that edits many values in place. `KitTest` fails on a kind no page has.
5. Write its section in `controls.md`, and add its row to the table above.

## What every row gets

`SettingRows` gives these to every kind, so a kind never builds them:

- The changed marker before the name, which resets on a press and names the default in its tooltip. Backspace resets too.
- Dimming under an off switch, a heading's switch or `dependsOn`. A dimmed row stays editable.
- A summary of its value while its parent is closed, such as `L, K` before Slot Lock's switch.
- Search over its name, its description and its values, which opens the rows that hold a hit and paints the words it matched.
- Narration of its name, kind, value, change and dimming, from `Setting.narration`.
- The keyboard: Up and Down move between rows, Enter and Space press, Left and Right step a value or open and close, and focus looks like hover.
- The description cut to two lines, with the whole of it in a tooltip.
- A star beside the name on a starred row and on the row under the mouse or the keys, which a press or the Favorite Key toggles, and Favorites, the first section, lists.
- Saving: a change applies at once, and the screen writes the config when it closes.
