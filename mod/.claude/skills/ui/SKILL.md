---
name: ui
description: Use when adding or changing anything the player sees or hears from ds-mod - chat lines, HUD, alerts, container overlays, tooltips, world rendering, edits to the server's HUD, settings text.
---

ds-mod looks and reads like SkyHanni and SkyOcean, the mods its players already know. Minecraft mods have their own conventions, so follow those two over web or app design habits. Their wording is the model for ours, and SkyOcean's named colors are the model for the text layer.

Minecraft looks Swiss and 16-bit: flat color, hard pixel edges, one font, a grid. Draw in that language, with text and flat shapes. Cards, rounded corners, soft shadows and slide-in panels belong to apps, and Minecraft's own toast box is one of them, so ds-mod has no toast.

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

[`surfaces.md`](surfaces.md) lists every type within each surface. Read it when the table leaves you between two, or when you build a surface for the first time.

## Use the surface's layer

Each surface has one shared layer: a plain model in `core` and one object in `edge` that draws it. The layers keep what they are given and redraw it themselves, so they are called when something changes, never every frame.

A feature lives in `core` and cannot call a layer. It returns its HUD block from `hud(now)` and its slot marks from `slots(now)`, and says something once with `notice(detail)` or `alert(title, detail)`. `Readouts` hands all of it to the layers. `mod/AGENTS.md` says how the other surfaces get a route.

| Surface | Model in `core` | Hand it to |
| --- | --- | --- |
| Chat | `text { }` | `Chat.send`, `Chat.notice` |
| HUD | `hud("Header") { row(..); bar(..) }` in a `HudGroup` | `Hud.show`, `Hud.hide` |
| Alert | `text { }` | `Alerts.title`, `Alerts.actionBar`, `Alerts.sound` |
| Container overlay | `SlotMark` | `Slots.mark`, `Slots.clear` |
| Tooltip | `text { }` per line | `Tooltips.add` |
| World | `Waypoint`, `BlockBox`, `Path`, `Ring` | `World.show`, `World.hide` |

A type in `surfaces.md` that no layer draws yet is added to that surface's layer, with its case in `SurfacesTest`. Edits to the server's HUD and the mod's own screens have no layer yet, so the first feature there builds it the same way.

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
- **HUD groups:** blocks that belong to one activity share a `HudGroup`, which stacks them and can put one flat background behind them all. Turn the background on where the group sits over busy scenery, and leave it off for a single line.
- **Setting names:** Title Case, one to three words. "Scale Tracker".
- **Setting descriptions:** one sentence that starts with a verb and ends with a period. "Show the scales you earn per hour." A requirement or warning follows as its own sentence.
- **Changed defaults:** when a feature changes something the server shows, tell the player once in chat and name the setting.
- **Spelling:** American English. Write game nouns as the server writes them in a capture, capitals included.

## Done when

A client game test shows the surface in a screenshot, as `SurfacesTest` does, and you have looked at the image. A feature that only fills a layer `SurfacesTest` already shows is done when its replay transcript reads right.
