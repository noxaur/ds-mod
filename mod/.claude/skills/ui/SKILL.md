---
name: ui
description: Use when adding or changing anything the player sees or hears from ds-mod - chat lines, HUD, alerts, container overlays, tooltips, world rendering, edits to the server's HUD, settings text, the mod's own screens and their controls, and any click, drag or key the player operates.
---

ds-mod looks and reads like SkyHanni and SkyOcean, the mods its players already know. Minecraft mods have their own conventions, so follow those two over web or app design habits. Their wording is the model for ours, and SkyOcean's named colors are the model for the text layer.

Minecraft looks Swiss and 16-bit: flat color, hard pixel edges, one font, a grid. Draw in that language, with text by role and chrome from the kit. Cards, rounded corners, soft shadows, gradients and slide-in panels belong to apps, and Minecraft's own toast box is one of them, so ds-mod has no toast.

## Pick the surface

A surface is a place the mod can put information. Choose it by how long the information lives and where the player is looking.

| The information is | Surface |
| --- | --- |
| Something that happened once, which the player may want to re-read | Chat |
| Something that needs action within seconds | Alert, plus a chat line |
| True for as long as an activity lasts | HUD |
| About one item | Tooltip |
| About a slot in an open container | Container overlay |
| About a place, block or entity | World |
| Already shown by the server, but badly | Edit the server's HUD, so the player sees it once |
| A choice the player makes | Settings |
| A task with several controls, such as placing the HUD | Own screen |
| Something the player does to what is already on screen, such as copying chat | Input on that screen |

[`surfaces.md`](surfaces.md) lists every type within each surface. Read it when the table leaves you between two, or when you build a surface for the first time.

## Use the surface's layer

Each surface has one shared layer: a plain model in `core` and one object in `edge` that draws it. The layers keep what they are given and redraw it themselves, so they are called when something changes, never every frame.

A feature lives in `core` and cannot call a layer. It returns its HUD block from `hud(now)` and its slot marks from `slots(now)`, and says something once with `notice(detail)` or `alert(title, detail)`. `Readouts` hands all of it to the layers. A handler keeps a server line out of chat with `hide()`. `mod/AGENTS.md` says how the other surfaces get a route.

| Surface | Model in `core` | Hand it to |
| --- | --- | --- |
| Chat | `text { }` | `Chat.send`, `Chat.notice` |
| HUD | `hud("Header") { row(..); bar(..) }` in a `HudGroup` | `Hud.show`, `Hud.hide` |
| Alert | `text { }` | `Alerts.title`, `Alerts.actionBar`, `Alerts.sound` |
| Container overlay | `SlotMark` | `Slots.mark`, `Slots.clear` |
| Tooltip | `text { }` per line | `Tooltips.add` |
| World | `Waypoint`, `BlockBox`, `Path`, `Ring` | `World.show`, `World.hide` |
| Own screen | `Theme` | The kit in `edge/ui` |

A type in `surfaces.md` that no layer draws yet is added to that surface's layer, with its case in `SurfacesTest`. Edits to the server's HUD have no layer yet, so the first feature there builds it the same way.

## Draw chrome with the kit

Chrome is everything that is not text: panels, buttons, slots, bars, marks. It is pixel art drawn in code by `Kit` in `edge/ui`, in the colors of the player's `Theme`. The `draw-with-kit` rule fails the build on a shape drawn anywhere else.

- **Grid:** one kit pixel is one GUI pixel, so chrome grows with Minecraft's GUI scale as vanilla's does. Every size and position is a whole number. The HUD alone has its own scale, which the player sets and `Readouts` snaps to whole screen pixels, so a 1px edge stays one pixel wide.
- **Frame:** a 1px outline with its four corner pixels left out, and inside it a 1px bevel, light on the top and left and shade on the bottom and right. That is a raised thing: a panel, a button, a tab, a scrollbar thumb. A sunk thing swaps the two edges and has no outline: a slot, a well, a bar's track, a checkbox.
- **Theme:** `Theme` in `core/ui` holds one color per token. Charcoal is the default and the one to design in. Vanilla matches Minecraft's own light gray screens, and the player picks between them in the settings. Draw with a token and both themes work. Check both in the screenshot.
- **Ink:** a label on a panel or a button is chrome. It is a plain string that `Kit.label` draws in the theme's ink, which is light in Charcoal and dark in Vanilla. Text colored by role reads only on a dark surface, so it sits on a well or on the HUD's plate, which are dark in both themes.
- **State:** a control shows idle, hover, pressed and off with its face and edges. Pressed also moves the label down 1px. Focus looks like hover, so the keyboard works without another style.
- **Icons:** white sprites of at most 7x7 in `textures/gui/sprites/`, which `Kit.icon` tints with ink or a role.

A screen is a vanilla `Screen` holding the kit's widgets. It takes the screen it was opened over and goes back to it on close, so a button in the settings returns to the settings. A screen says little: a title, its controls and one line that reads back what the player did. They are vanilla widgets with a kit face, so focus, narration and the keyboard work as they do in Minecraft. The settings screen is MoulConfig's and keeps its own look.

[`kit.md`](kit.md) lists every token, component and size. Read it before you build a screen, or when you need a component it may not have.

## Design the control whole

Anything the player operates, on the mod's own screen or on a vanilla one, ships finished. The first cut is the finished feature, so a convention the player has to ask for later is a missing part of it.

1. Name the kind of control: list selection, context menu, range slider, text field, draggable thing, scrolling list.
2. Read its section in [`controls.md`](controls.md). Survey the peer mods listed there for what players of that kind already know.
3. Build every convention that applies, with the keyboard and the mouse, before the first commit.
4. Say in the PR which conventions you left out, and why.

A kind that `controls.md` lacks gets its section there once the survey is done. A kit widget holds its kind's conventions, so a second control of that kind reuses the widget. Put the rules of a control in `core`, as `Sections` does for the slider, so a unit test can drive each one.

## Color by role

Text is built with `text { }` from `core.text`, where each piece states what it means and the role owns the color. The `text-by-role` rule fails the build on a raw color or a `§` code.

| Role | Use it for |
| --- | --- |
| `body` | The sentence |
| `value` | The one number or name the line is about. Pass a number as a number, and it gets thousands separators |
| `muted` | Labels, units, sub-rows, anything secondary |
| `action` | What a click will do |
| `good`, `bad`, `warning` | State only: done, failed, about to fail |
| `brand` | The mod's name and a feature's header |

One line has one `value`. A line where everything is highlighted has no highlight.

## Wording

- **Chat:** one short sentence that says what happened, then what to do, ending in a period. "Sold 1,204 scales."
- **Notices:** a low-urgency update is a chat line through `Chat.notice`, which SkyOcean uses in place of a toast. "Scale Tracker | Dragon Scale x12".
- **Errors:** all `bad`, and they name the fix. "Open the storage menu to load your items."
- **Exclamation marks:** alerts only, so an alert stands out.
- **Click hints:** "Click to <verb>." in `action`.
- **Titles:** one to three words in one role, such as "Boss Ready". `Alerts.title` takes the chat line that goes with it, which says which feature fired so the player can turn it off.
- **HUD:** a Title Case header, then `Label: value` rows. `hud { }` colors the header and the labels.
- **HUD groups:** blocks that belong to one activity share a `HudGroup`, which stacks them on one plate. The player owns the plate, the scale and the place of every block, so a feature sets none of them.
- **Buttons:** one or two words in Title Case that name what the press does. "Reset", "Done".
- **Setting names:** Title Case, one to three words. "Scale Tracker".
- **Setting descriptions:** one sentence that starts with a verb and ends with a period. "Show the scales you earn per hour." A requirement or warning follows as its own sentence.
- **Changed defaults:** when a feature changes something the server shows, tell the player once in chat and name the setting.
- **Spelling:** American English. Write game nouns as the server writes them in a capture, capitals included.

## Done when

A client game test shows the surface in a screenshot, as `SurfacesTest` does, and you have looked at the image. A screen or a kit component is shown in both themes. A feature that only fills a layer `SurfacesTest` already shows is done when its replay transcript reads right. A control is done when each convention of its kind is built or named in the PR as left out, and a test drives each press, drag and key.
