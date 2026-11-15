# Kit

The pixel kit that draws ds-mod's chrome. `Theme` in `core/ui` owns the colors, `Kit` in `edge/ui` owns the shapes, and the widgets beside it own input. Sizes are in GUI pixels.

The gallery is the kit's picture: `/dskit` in the debug build opens it, and `KitTest` screenshots it in both themes and at two GUI scales. Open those images before you design, since they show what the kit already has.

## Tokens

A token names a job, and each theme gives it a color. `Theme.kt` has the values.

| Token | Its job |
| --- | --- |
| `outline` | The line around a raised thing |
| `panel`, `panelLight`, `panelShade` | A screen's background and its bevel |
| `raised`, `raisedLight`, `raisedShade` | A button at rest |
| `hover`, `hoverLight`, `hoverOutline` | A control under the mouse or holding focus |
| `down` | A pressed control |
| `slot`, `slotLight`, `slotShade` | An item slot |
| `well`, `wellLight`, `wellShade` | A sunk area that is dark in both themes: lists, fields, bar tracks, anything holding role text |
| `tabOff` | A tab that is not selected |
| `thumb`, `thumbLight` | A scrollbar's thumb |
| `ink`, `inkMuted`, `inkOff` | Labels on a panel or a control: normal, secondary, disabled |
| `inkShadow` | Whether ink has Minecraft's text shadow. Dark ink on a light panel has none |
| `accent` | The selected thing |
| `plate`, `plateEdge` | The translucent backing of the HUD |

A new theme is one more entry in `Theme` with every token filled. A new token is added to every theme in the same change, and only when no existing job fits.

## Shapes

`Kit` draws these and keeps no state. Each call is a few fills, so a screen calls them every frame with numbers it worked out in `init`.

| Shape | Size | What it is |
| --- | --- | --- |
| `panel` | Any | The frame of a screen. Content starts 7 in from its edge, as in vanilla |
| `button` | 16 tall | The raised face of a control in one of four states: idle, hover, pressed, off |
| `well` | Any | A dark sunk area |
| `slot` | 18x18, or 26x26 | A sunk square with a 16x16 inside for an item. The big one is a crafting result's |
| `plate` | Any | The HUD's backing: a translucent fill inside a notched ring |
| `glass` | Any | A panel the world shows through: the plate's fill at an alpha the player sets, inside the plate's ring. Dark in both themes, so ink on it is `GLASS_INK` |
| `glassSlot`, `glassWell` | 18x18, or any | A sunk square on glass, see-through at any alpha, so a clear menu still shows its grid |
| `button` on glass | 16 tall | `button` with `glass = true`: a lighter pane raised off the glass in the plate's ring, ringed in `hoverOutline` under the mouse and sunk into a `glassWell` while pressed, with its label in `GLASS_INK`. `KitButton.glass` asks for it each frame |
| `glassTab` | 22x19, or 17 when not selected | A tab on glass. It stops at the panel's ring, since two see-through fills on each other show a darker band, so the selected one stands taller instead of joining |
| `hotbar` | 20 per cell plus 2, by 22 | A strip of slots cut from the game's own hotbar sprite, for a HUD block in the Hotbar look. The one piece of chrome that is not the kit's, because it has to match the player's hotbar under their resource pack |
| `tooltip` | Text plus 4 on each side | A plain tooltip's backing: the plate's colors, nearly opaque. A tooltip with a style of its own keeps it |
| `bar` | 6 tall | A well with a 4px fill that has a lit top row and a shaded bottom row |
| `scrollbar` | 6 wide | A well with a raised thumb |
| `tab` | 22x21, or wider to hold a field | Sits on a panel's top or bottom edge. The selected one joins the panel, and the rest sit 2px further out in `tabOff`, or in `hover` under the mouse. Draw the rest before the panel and the selected one after it |
| `checkbox` | 10x10 | A well that holds the check icon when on. Hover and focus ring it in `hoverOutline` |
| `span` | Any, 14 tall in a slider | A switched-on stretch of a range slider: a raised face in `accent`, lighter under the mouse and sunk while dragged |
| `tick` | 1 wide | A mark across a track where its scale changes |
| `flash` | Any | A white wash over something the player just acted on, which fades out. The same in both themes, because it lies on the game and not on a panel |
| `divider` | 2 tall | A shade line over a light line, cut into the panel |
| `ring` | Any | A notched 1px line in `accent` or a role, around the selected thing |
| `icon` | Up to 7x7 | A tinted sprite: check, cross, warn, up, down, left, right, search, arrow, lock, back, book, star. MoulConfig's settings screen draws one through its own context |
| `line` | Any | A 1px stepped line between two points, with a dark line beside it, for joining two things that belong together |
| `field` | Any, 16 tall in a text field | A well that a text field holds, ringed in `hoverOutline` under the mouse or with focus |
| `label` | One line | A string in ink |
| `slotFill`, `slotBorder`, `slotDim` | 16x16 inside, 18x18 border | Plain marks on a container's slots, colored by role: a fill behind the item, a frame around the slot, a dimming over the item |
| `veil` | Any | A wash in the panel's color over a part that is loading or has nothing to show, with a notice centered on it |
| `bone` | Any | A block standing in for text or an icon while a layout loads, so the screen keeps its shape under its veil |
| `slotHover` | 16x16 inside | The white wash over the slot under the mouse, in a grid the mod draws itself, as a menu lights its own |
| `slotArt`, `slotGem` | 18x18, 7x7 | A rarity mark's back behind the item and its gem over it, cut from an `Atlas` that `SlotArts` bakes from the player's style |
| `inventory`, `slotAt` | 176x166, 18x18 | The player's inventory, and one empty slot of it, from the resource pack's own texture, for a preview that must match the real screen |

## Widgets

A widget is a vanilla widget with a kit face. It takes its label as a string and its action as a lambda.

| Widget | What it is |
| --- | --- |
| `KitButton` | A button with a label or an icon. An icon button is 16x16 |
| `KitCheckbox` | A checkbox with its label 4px to the right. On a well its label is body text, since ink would not read there |
| `KitTab` | A tab on a menu's top or bottom edge, with an item or an icon, that runs an action. Its `selected` state joins the panel |
| `KitStepper` | A minus button, a value and a plus button, for a number with few useful steps |
| `KitTextField` | Vanilla's `EditBox` on a 16 tall `field`, with its text 4 in. A press anywhere on the well lands |
| `KitKeysButton` | A button that shows keys and records new ones, held together or in order, as the key bind section of `controls.md` says. Keys it shares are ringed in a role |
| `KitCycleButton` | A button that steps through a few values, as the cycle button section of `controls.md` says |
| `KitMenu` | A panel of actions beside the mouse, opened by a right press, as the context menu section of `controls.md` says. The item under the mouse or picked with Up and Down is lit |
| `KitRangeSlider` | A 16 tall track with any number of spans, for picking stretches of a scale. Press and drag on the track to draw one or click to add one, drag an end or the middle, right-click to remove. The cursor shows what a press will do. `Sections` in `core/ui` holds its rules: a span is at least 5 long, and two within 3 of each other become one |

Controls in a row are 4 apart, and rows are 4 apart. A label beside a control sits on the control's text baseline.

## The game's menus

`MenuSkin` in `edge` draws the game's own menus with these shapes when Themed Menus is on: a `panel` over the menu, a `slot` under each slot, a `well` behind the player in the inventory, and an arrow from crafting slots to the result. Their labels take the theme's ink. The Glass look draws `glass`, `glassSlot`, `glassWell`, `glassTab` and glass buttons in their place, with labels in `GLASS_INK`. Only menus whose texture is just those parts are drawn, so a new kind of menu is added to `MenuSkin.skins` and to `MenuBackgroundMixin` together.

## Adding to the kit

Add a shape when a screen needs one the table lacks, and a widget when the shape takes input. Build it from tokens and the frame, show every state of it in the gallery, and add its row here. A shape that only one screen will ever draw stays in that screen, built from the kit's shapes.
