# Capture format

Each line has a time `t`, a `type` and `data`. A type is either an event or a raw record.

An event's text is `plain` plus `spans`, with `§` codes already resolved into the spans. A raw record's text is plain `text` plus the full `component`. `itemcomponents`, `entities`, `skulls` and `blockstates` record all entries once with `"full": true`, then only the entries that changed, with null for a removed one.

`actionbar`, `death` and `sidebar` were raw records until 2026-11-14, with another shape, so an older capture cannot be cut for them.

## Events

The `GameEvent`s that features see, recorded as they were posted. `readCapture` gives them back in a test.

- `system`: a line in chat. Player chat arrives this way too.
- `world`: entering a world.
- `tablist`: the rows of the tab list in the order the client draws them, when one changes.
- `sidebar`: the sidebar's title and its lines from the top, when one changes.
- `actionbar`: the line above the hotbar, each time the server sends it.
- `death`: the server's death event on an entity that is not a player, with its type, name and position.
- `head`: a player head block the server placed, with its position.
- `pet`: the pet nearest the player, an armor stand within 6 blocks named like `[Lvl 100] Ender Dragon`, with its name and the head it wears, when the nearest one changes.
- `container`: a menu the player opened, or their own inventory again. `slot`: each slot that changed, with the item's id, count, name, lore and `custom_data`, and how it looks: a head's `skin`, leather's `dye`, the `glint` override, `foil` (whether the game draws the shine at all), an `item_model` other than the item's own as `model`, and the tooltip `style`. A big menu's slots arrive over a few ticks.

## Raw records

Written for an agent to read. No feature sees one until it is promoted to an event.

- `chat` and `title`.
- `bossbars`, and `tabheader` for the tab list's header and footer.
- `itemcomponents`: the components a `slot` leaves out, such as `item_model`, and the full `profile`, in Minecraft's own codec JSON.
- `entities`: players, named entities and anything wearing or holding an item, each with the block it stands in. Glowing entities, silverfish and creepers, displays (`text`, `item`), item frames (`item`, `rotation`, `facing`) and dropped named items come too.
- `status`: the player's block, health and XP.
- `sound`, `particles` and `block`: what the server played, spawned or changed.
- `swing` and `use`: the player's attack and use buttons, before the game acts, with position, yaw, pitch, the held item's `item`, `name` and `custom_data` `itemId`, and the `target` entity or `block` under the crosshair.
- `unload`: a living entity the client dropped, with its `reason`. A mob that only left has `unload` and no `death` event.
- `skulls`: skull blocks within four chunks, with `state` (rotation) and `profile`. `blockstates`: the blocks in `BlockScan.kt`'s watch-list there. Both are keyed `x,y,z` and include what stood there before the capture began.
- `map`: a map the player carries or an item frame holds, when it changes. `colors` is base64 of the zlib-deflated 128x128 color bytes, present only when the pixels changed, and `decorations` are the markers.
- `hud`: the rows of each HUD block ds-mod showed. `metrics`: how long each feature took and what it counted. Its `capture-items` and `capture-entities` timers are what the recorder's own passes cost the client thread.
- `step`: the brief's step that starts on that line, with its `index` and `do`, and `done` after the last one. Cut a fixture's window from these.
