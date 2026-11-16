# Settings

ds-mod's settings screen draws every setting from one tree, `SettingsTree` in `core/config`. The tree is the only schema of the settings, and the one place a setting's name and description live: each line binds a field of `DsModConfig` to a kind of row, and the screen gets its control, search, reset and keyboard from the kind. Every setting is edited in the screen itself, and only an editor that needs the game behind it, such as Move HUD, opens a screen of its own. `/dsmod` opens the screen, and `/dskit rows`, `/dskit lists` and `/dskit panels` show every kind in the gallery.

## Add a setting

1. Add the field to its class in `DsModConfig` with a default, and nothing else: no annotation, no name. The default is what the changed marker and reset use, read from a fresh `DsModConfig()`, so write it nowhere else. The field's name is its key in the player's config file, so never rename one that shipped. A new class of values needs `@Serializable`.
2. Add one line to `SettingsTree` in its section and group, with a kind from the table below. Write no drawing or input code.
3. Run `SettingsTreeTest`. It fails on a saved field that no row reaches, on two rows that reach one field, on a repeated id, and on a name or a description worded against the ui skill.

A field that no row should edit goes on `SettingsTree.STORED` with the reason. It is empty, because every field the player sets has a row: a list of things the player made is an Items row, and a canvas is a Panel row. A row that edits several fields at once names the rest with `Bind.spanning`.

| Kind | Declare it with | For |
| --- | --- | --- |
| Switch | `switch(c.end::dragonTimer, "Dragon Timer", "Show when the egg returns.")` | On or off |
| Switch with settings | `switch(..) { keys(..); number(..) }` | A feature with settings of its own, which open under it and dim while it is off |
| Number | `number(g::cameraDistance, "Distance", "Set how far the camera sits.", 1f..50f, step = 0.5f, unit = "°")` | A number in a range, stepped from its minimum |
| Choice | `choice(c.loot::lootAlertTier, "Alert Tier", "Pick the lowest tier that alerts you.")` | One value of an enum, labeled by its `toString` |
| Pick | `pick(hud::theme, "Theme", "Pick the colors of the screens.") { Look.Swatch(it) }` | A value chosen by how it looks |
| Keys | `keys(g::freeLookKey, "Key", "Pick the key for Free Look.")` | One key or mouse button, as a GLFW code or -1 |
| Span | `span(c.end::beamSections, "Beam Timing", "Pick when the beam shows.", max = 200)` | Stretches of a scale, by `Sections`' rules |
| Fold | `fold("customLook", "Custom Look", "Build your own look.") { .. }` | Rows the player rarely needs, under a bar |
| Action | `action("resetAnimations", "Reset Animations", "Put every Animations setting back.", button = "Reset", run = g::resetAnimations)`, or `action(Opens.HUD_EDITOR, "Move HUD", "..", button = "Move", id = "moveHud")` | A button: a reset of a group, or a screen of the edge's that `DsModClient` registers for its `Opens`, only for an editor that needs the game behind it or a screen that is not a setting, such as Contribute's requests |
| Items | `items(binds, "Binds", "Name each bind, pick its keys and choose where it works.", CommandBinds)` | A list of things the player made, in groups, each a line of cells with details under it, by an `ItemKind` in `core`. Its value is a `Grouped`, bound across the list's fields with `Bind.spanning` |
| Panel | `panel(Bind.of(ui::tabs), "Tabs", "Press a place to add a tab there, and drag a tab to move it.", Canvas.MENU_TABS)` | A canvas the player edits by pointing, such as the HUD layout or the menu tabs, drawn by the `Editor` that `DsModClient` registers for its `Canvas` |
| Order | `order(g::equipmentPieces, "Pieces", "Pick the pieces to show, and drag them into order.")` | Which values of an enum show and in what order, as a `MutableList` field |
| Preview | `preview("modelPreview", "Preview", "Drag your model to turn it, and see the settings below on it.", Canvas.PLAYER_MODEL)` | A picture of what the rows around it do, which holds no value, drawn by the `Editor` that `DsModClient` registers for its `Canvas` |

A group whose rows are one feature takes its switch beside the heading: `group("Free Look", switch = toggle(g::freeLook, "Free Look", "Look around in third person.")) { .. }`. A row that needs a switch elsewhere names it with `dependsOn`. Either one dims the row while the switch is off.

The id defaults to the field's name. Pass `id` when that would repeat one, and never change an id once it ships, because a link to the setting names it.

A row bound to part of a value, such as one part of a rarity style, takes a `Bind` with a `get` and a `set` and names the part it reaches, as `Inventory.inventoryStyle.border`. Its `set` does what the existing editor does to the whole, such as switching the style to Custom.

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
5. Write its section in `controls.md` after surveying the peers, and add its row to the table above.

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
