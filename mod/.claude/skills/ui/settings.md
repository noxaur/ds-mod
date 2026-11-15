# Settings

ds-mod's own settings screen draws every setting from one tree, `SettingsTree` in `core/config`. The tree is the schema of the settings: each line binds a field of `DsModConfig` to a kind of row, and the screen gets its control, search, reset and keyboard from the kind. `/dssettings` opens the screen in the debug build, and `/dskit rows` shows every kind in the gallery.

Until the screen replaces MoulConfig's, a setting the player sees is also annotated for MoulConfig in `DsModConfig`, as the fields beside it are.

## Add a setting

1. Add the field to its class in `DsModConfig` with `@Expose` and a default. The default is what the changed marker and reset use, read from a fresh `DsModConfig()`, so write it nowhere else.
2. Add one line to `SettingsTree` in its section and group, with a kind from the table below. Write no drawing or input code.
3. Run `SettingsTreeTest`. It fails on a saved field that no row reaches, on two rows that reach one field, on a repeated id, and on a name or a description worded against the ui skill.

A field that no row should edit, such as a list an editor screen keeps, goes on `SettingsTree.STORED` with the reason.

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
| Action | `action(hud::edit, "Move HUD", "Drag a block to move it.", button = "Move")` | A button that opens an editor screen, through a `Runnable` field `DsModClient` sets |

A group whose rows are one feature takes its switch beside the heading: `group("Free Look", switch = toggle(g::freeLook, "Free Look", "Look around in third person.")) { .. }`. A row that needs a switch elsewhere names it with `dependsOn`. Either one dims the row while the switch is off.

The id defaults to the field's name. Pass `id` when that would repeat one, and never change an id once it ships, because a link to the setting names it.

A row bound to part of a value, such as one part of a rarity style, takes a `Bind` with a `get` and a `set` and names the part it reaches, as `Inventory.inventoryStyle.border`. Its `set` does what the existing editor does to the whole, such as switching the style to Custom.

## Wording

- A name is Title Case, three words or fewer, and says what the setting is: "Slot Lock", "F5 Views".
- A description is one sentence that starts with a verb and ends with a period. A row shows two lines of it and the rest in a tooltip, so put what matters first.
- A choice's values read in Title Case, from the enum's `toString`.

## Add a look

A pick shows each value as a `Look`, a case in `core/ui/Look.kt` that `Looks` in `edge/ui` draws 18 tall. Add the case, its width and its drawing in `Looks`, built from the kit's shapes, and use it in a `pick`. `KitTest` fails until the gallery's page of rows has a pick that shows it, so add one there.

## Add a kind

Only when no kind fits, and ask the user first, since every kind is one more thing a player learns.

1. Add the class to `Settings.kt` in `core/config`, under `Valued` when it holds a value, with its rules: stepping, clamping, its summary, its default's label, its search labels, and its value as the narrator says it in `spoken`. Test each rule in `SettingsTest`.
2. Add its entry to `Kind`, with the word the narrator calls it, and its builder to `Rows`.
3. Give it a face in `SettingRows` in `edge/ui`: where its control sits in `measure`, what it shows in `refresh`, how it draws in `face`, and what a press, a drag, the wheel and each key do. Draw with the kit and text by role.
4. Show it idle, changed and dimmed on the gallery's page of rows. `KitTest` fails on a kind the page lacks.
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
- Saving: a change applies at once, and the screen writes the config when it closes.
