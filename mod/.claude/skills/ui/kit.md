# Kit

The pixel kit that draws ds-mod's chrome. `Theme` in `core/ui` owns the colors, `Kit` in `edge/ui` owns the shapes, and the widgets beside it own input. Sizes are in GUI pixels.

The gallery is the kit's picture: `/dskit` in the debug build opens it, and `/dskit rows` its page of settings rows, every kind with every look, `/dskit lists` its page of Items rows and `/dskit panels` its page of Panel rows. `KitTest` screenshots every page in both themes, the first at two GUI scales, and fails on a kind of row or a look the page lacks. Open those images before you design, since they show what the kit already has.

## Tokens

A token names a job, and each theme gives it a color. `Theme.kt` has the values.

| Token | Its job |
| --- | --- |
| `outline` | The line around a raised thing |
| `panel`, `panelLight`, `panelShade` | A screen's background and its bevel |
| `side` | A strip a step back from the panel, such as a settings sidebar or a tab bar |
| `raised`, `raisedLight`, `raisedShade` | A button at rest |
| `hover`, `hoverLight`, `hoverOutline` | A control under the mouse or holding focus |
| `down` | A pressed control |
| `slot`, `slotLight`, `slotShade` | An item slot |
| `well`, `wellLight`, `wellShade` | A sunk area that is dark in both themes: lists, fields, bar tracks, anything holding role text |
| `tabOff` | A tab that is not selected |
| `thumb`, `thumbLight` | A scrollbar's thumb |
| `ink`, `inkMuted`, `inkOff` | Labels on a panel or a control: normal, secondary, disabled |
| `inkMatch` | The words of a search in a label on a panel or a control, such as a dropdown's value. On a well, the `match` role does this job |
| `inkShadow` | Whether ink has Minecraft's text shadow. Dark ink on a light panel has none |
| `accent` | The selected thing |
| `plate`, `plateEdge` | The translucent backing of the HUD |

A new theme is one more entry in `Theme` with every token filled. A new token is added to every theme in the same change, and only when no existing job fits.

## Shapes

`Kit` draws these and keeps no state. Each call is a few fills, so a screen calls them every frame with numbers it worked out in `init`.

| Shape | Size | What it is |
| --- | --- | --- |
| `panel` | Any | The frame of a screen. Content starts 7 in from its edge, as in vanilla |
| `backdrop` | Any | A panel's face with no frame, for a screen that fills the window |
| `side` | Any | A strip in `side` with an outline down its right edge, beside a `backdrop` |
| `navRow` | 16 tall, or 13 for a subsection | A line of a navigation tree on a `side`. Hover and keyboard focus lift its face, and the selected one lifts further and has a 2px ink bar on its left |
| `swatch` | Any square, 9x9 for a section | A color in a 1px outline: a placeholder icon, or a color to pick |
| `rule` | 1 tall | A faint line across a panel, such as after a heading, or between the rows of a well |
| `button` | 16 tall | The raised face of a control in one of four states: idle, hover, pressed, off |
| `well` | Any | A dark sunk area |
| `slot` | 18x18, or 26x26 | A sunk square with a 16x16 inside for an item. The big one is a crafting result's |
| `plate` | Any | The HUD's backing: a translucent fill inside a notched ring |
| `glass` | Any | A panel the world shows through: the plate's fill at an alpha the player sets, inside the plate's ring. Dark in both themes, so ink on it is `GLASS_INK` |
| `glassSlot`, `glassWell` | 18x18, or any | A sunk square on glass, see-through at any alpha, so a clear menu still shows its grid |
| `button` on glass | 16 tall | `button` with `glass = true`: a lighter pane raised off the glass in the plate's ring, ringed in `hoverOutline` under the mouse and sunk into a `glassWell` while pressed, with its label in `GLASS_INK`. `KitButton.glass` asks for it each frame |
| `glassTab` | 22x19, or 17 when not selected | A tab on glass. It stops at the panel's ring, since two see-through fills on each other show a darker band, so the selected one stands taller instead of joining |
| `hotbar` | 20 per cell plus 2, by 22 | A strip of slots cut from the game's own hotbar sprite, for a HUD block in the Hotbar look. The one piece of chrome that is not the kit's, because it has to match the player's hotbar under their resource pack |
| `gameScreen` | Any, with the size of one of the screen's GUI pixels | A template of the game's screen: sky over ground split at the horizon, the crosshair, and the hotbar at the screen's own size, for a preview of where something sits on screen. The same in both themes, because it pictures the game |
| `tooltip` | Text plus 4 on each side | A plain tooltip's backing: the plate's colors, nearly opaque. A tooltip with a style of its own keeps it |
| `bar` | 6 tall | A well with a 4px fill that has a lit top row and a shaded bottom row |
| `scrollbar` | 6 wide | A well with a raised thumb |
| `thumb` | Any | A scrollbar's thumb alone, idle, hover or off, for a track the screen draws itself |
| `barFill` | Any | A bar's fill alone, with its lit top row and shaded bottom row |
| `arrow` | Any, 24x16 in a furnace | A flat sunk arrow, right or down, filled along its length to a fraction in a role, for progress |
| `flame` | 14x14 | A sunk flame filled from the bottom to a fraction, for fuel burning |
| `bubbles` | 12x29 | A column of sunk dots lit from the bottom to a fraction, for brewing |
| `tab` | 22x21, or wider to hold a field, or taller to hold the creative inventory's item | Sits on a panel's top or bottom edge. The selected one joins the panel, and the rest sit 2px further out in `tabOff`, or in `hover` under the mouse. Draw the rest before the panel and the selected one after it |
| `tabBar` | 18 tall | A strip in `side` across the top of a pane, with a 1px outline along its bottom for browser tabs to sit on |
| `pageTab` | 15 tall, 44 to 92 wide | A browser tab on a `tabBar`: a swatch or an icon, a label in muted ink, and a × that shows under the mouse and on the selected one, in a 9x9 square that mirrors the swatch's. Hover lifts it to `hover`, the selected one is 17 tall in `panel` and covers the line to join the pane, and DOWN is the selected one while dragged, outlined in `hoverOutline` |
| `newTab` | 15x15 | The plus after the last `pageTab`, which opens a new one |
| `tag` | One line | Role text on a `tabBar`, on its tabs' title line, with a shadow in both themes, such as the brand at its end |
| `checkbox` | 10x10 | A well that holds the check icon when on. Hover and focus ring it in `hoverOutline` |
| `switch` | 22x12 | A sunk track with a raised 10x10 knob, on the right in the good role when on. Hover and focus outline the knob in `hoverOutline`. Off, for a switch the player cannot turn now, the knob is a flat `panel` square |
| `grip` | 3x7 | Two columns of four dots on a well, at the start of a line that drags, lit under the mouse or the keys |
| `dropdown` | Any, 16 tall, or 22 with a preview | A raised face with a label, after a preview when it has one, and the down arrow in a square that mirrors the label's start. `dropdownWidth` and `dropdownLabel` measure it |
| `marker` | 3x3 | A setting's changed dot in `accent`, ringed in `hoverOutline` under the mouse |
| `wellRow` | Any | A faint lift of a well behind a row under the mouse or the keyboard |
| `dim` | Any | A wash in the well's color over a row that is dimmed and still editable |
| `guide` | 1 wide | A faint line down the left of rows that open under another |
| `themeSwatch` | 22x13 | A small panel in another theme's colors with a well and a button, as a preview of that theme |
| `tile` | Any | A tile of a menu of previews, ringed in `accent` when selected, and lit in `hover` under the mouse or the keys |
| `span` | Any, 14 tall in a slider | A switched-on stretch of a range slider: a raised face in `accent`, lighter under the mouse and sunk while dragged |
| `tick` | 1 wide | A mark across a track where its scale changes |
| `flash` | Any | A white wash over something the player just acted on, which fades out. The same in both themes, because it lies on the game and not on a panel |
| `divider` | 2 tall | A shade line over a light line, cut into the panel |
| `ring` | Any | A notched 1px line in `accent` or a role, around the selected thing |
| `icon` | Up to 7x7 | A tinted sprite: check, cross, warn, up, down, left, right, search, arrow, lock, back, book, star. |
| `line` | Any | A 1px stepped line between two points, with a dark line beside it, for joining two things that belong together |
| `field` | Any, 16 tall in a text field | A well that a text field holds, ringed in `hoverOutline` under the mouse or with focus |
| `label` | One line | A string in ink, or a `Kit.Marked` label with what a search matched in `inkMatch`, cut and measured when the query changes |
| `slotFill`, `slotBorder`, `slotDim` | 16x16 inside, 18x18 border | Plain marks on a container's slots, colored by role: a fill behind the item, a frame around the slot, a dimming over the item |
| `veil` | Any | A wash in the panel's color over a part that is loading or has nothing to show, with a notice centered on it |
| `bone` | Any | A block standing in for text or an icon while a layout loads, so the screen keeps its shape under its veil |
| `cooldown` | 16x16 inside | The game's own cooldown wash over an item: half-white over the part of the wait still to run, draining down. The same in both themes, because it lies on the item |
| `slotHover` | 16x16 inside | The white wash over the slot under the mouse, in a grid the mod draws itself, as a menu lights its own |
| `slotHighlightBack`, `slotHighlightFront` | 24x24 around the item | Vanilla's own slot highlight, behind the item and over it, for a slot the mod adds to a server menu, so it lights as the menu's items do |
| `slotArt`, `slotGem` | 18x18, 7x7 | A rarity mark's back behind the item and its gem over it, cut from an `Atlas` that `SlotArts` bakes from the player's style, or from any row of a bigger atlas |
| `inventory`, `slotAt` | 176x166, 18x18 | The player's inventory, and one empty slot of it, from the resource pack's own texture, for a preview that must match the real screen |

## Widgets

A widget is a vanilla widget with a kit face. It takes its label as a string and its action as a lambda.

| Widget | What it is |
| --- | --- |
| `KitButton` | A button with a label or an icon. An icon button is 16x16, and `show` swaps its icon and its tooltip, as Collapse All and Expand All do |
| `KitCheckbox` | A checkbox with its label 4px to the right. On a well its label is body text, since ink would not read there |
| `KitTab` | A tab on a menu's top or bottom edge, with an item or an icon, that runs an action. Its `selected` state joins the panel |
| `KitSlotButton` | A slot the mod adds to a menu, showing an item, that runs an action. It draws the slot's frame in the menu's theme, and vanilla's slot highlight under the mouse, as the menu's own items get |
| `KitStepper` | A minus button, a value and a plus button, for a number with few useful steps |
| `KitTextField` | Vanilla's `EditBox` on a 16 tall `field`, with its text 4 in, or after an `icon` such as search. A press anywhere on the well lands |
| `KitTabBar` | Browser tabs across the top of a pane, as the tab bar section of `controls.md` says. `SettingsTabs` and `TabStrip` in `core/ui` hold its rules |
| `KitNavTree` | Sections with a swatch and their subsections on a `side`, as the navigation tree section of `controls.md` says. `NavTree` in `core/ui` holds its rules, and `Scroll` its scrolling |
| `KitKeysButton` | A button that shows keys and records new ones, held together or in order, as the key bind section of `controls.md` says. Keys it shares are ringed in a role |
| `KitCycleButton` | A button that steps through a few values, as the cycle button section of `controls.md` says |
| `KitMenu` | A panel of actions beside the mouse, opened by a right press, as the context menu section of `controls.md` says. The item under the mouse or picked with Up and Down is lit, and typing lights one by its first letters, as `TypeAhead` in `core/ui` rules. With a `checked` item and `below`, it is a dropdown's menu |
| `KitPickMenu` | A pick's menu: a row of tiles under its dropdown, each a `Look` over its name, as the pick section of `controls.md` says. Typing lights a tile as in `KitMenu` |
| `SettingRows` | The rows of a settings screen, each a `Setting` with the face of its kind, and the behaviors every row shares, as `settings.md` says. It lays out on change and draws only the rows that show. Its `narration` is one entry of the screen's narration, which the screen adds with `addWidget` |
| `Editor` | A row that edits many values in place, such as `ItemsEditor` for a list and the editors of a canvas. `SettingRows` hosts it under the row's header, lays it out on change, hands it the input while the player is inside it, and draws its menus over the rows |
| `Looks` | Draws each `Look` a pick shows, 18 tall. Slot looks come from one atlas of every border, fill and floor alone, painted once |
| `KitRangeSlider` | A 16 tall track with any number of spans, for picking stretches of a scale. Press and drag on the track to draw one or click to add one, drag an end or the middle, right-click to remove. The cursor shows what a press will do. `Sections` in `core/ui` holds its rules: a span is at least 5 long, and two within 3 of each other become one |

Controls in a row are 4 apart, and rows are 4 apart. A label beside a control sits on the control's text baseline.

Padding mirrors. What sits at the end of a row, a tab or a field, such as a × or an arrow, takes a square as wide as what sits at its start and the same gap from the edge, so the two are centered alike. An outline is an edge, so content keeps the same gap from a strip's outline as from the window's edge, and a strip's content has the same gap on every side.

## The game's menus

`MenuSkin` in `edge` draws every one of the game's container screens with these shapes when Menu Theme is on: a `panel` over the menu, a `slot` under each slot, a big one under each result, and `well`s where the texture had dark areas. The game draws each moving part as a named sprite, and `MenuSkin`'s table swaps each name for a shape: a progress `arrow`, a `flame`, `bubbles`, a `thumb`, a button face, a `barFill`. Their labels take the theme's ink. The Glass look draws `glass`, `glassSlot`, `glassWell`, `glassTab` and glass buttons in their place, with labels in `GLASS_INK`. A sprite the table lacks keeps the game's art, so a new part in a Minecraft update gets its row there.

## Adding to the kit

Add a shape when a screen needs one the table lacks, and a widget when the shape takes input. Build it from tokens and the frame, show every state of it in the gallery, and add its row here. A shape that only one screen will ever draw stays in that screen, built from the kit's shapes.
