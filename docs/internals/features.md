# Features

How a feature gets its input and hands back what it shows. The contract an agent writes against is in the [`feature` skill](../../mod/.claude/skills/feature/SKILL.md).

## Features run on events and ticks, never in a frame

A feature handles events, and `Game` rebuilds its HUD block and slot marks after an event the feature could see, and four times a second for its clocks. The frame only draws what the last rebuild left. A feature's code then can't cost a frame, however it is written, and its cost is measured in one place.

A feature returns what it shows instead of calling a UI layer. The replay test can then read it without Minecraft, and a feature that is switched off shows nothing without having to remember to hide. `Game` compares each rebuilt readout with the last one, and `Readouts` in the edge hands only a changed one to the layers.

A feature's HUD block is built with `block { }`, which heads it with the feature's title. A feature can't then retype or misname itself, and the header is built once.

Hiding a server line is the one thing a handler decides on the spot. Fabric asks whether a line may show before it reaches the chat, so `Game.post` returns the answer and the edge posts chat from that question. A line another mod refuses first never reaches the features, because Fabric stops asking at the first refusal.

A chat line or an alert is collected the same way and delivered at the end of the tick. Printed from inside the handler, it would land above the server line that caused it, because the handler runs before that line reaches the chat.

Placement belongs to `Readouts`, which keeps one position per feature id. A feature returns a block and knows nothing about the screen.

`Slots` drops its marks when a container screen closes, and marks handed over earlier may be for the container that was open before. So `Readouts` clears the marks when a container screen opens and hands them over again on the next tick, after the poll has seen the new container. It hands them over again when the screen closes too: closing the player's own inventory changes no container, so no event would bring the hotbar's marks back.

## Shared state is folded once

`Game` folds the tab list, the sidebar and the open container into `TabState`, `SidebarState` and `ContainerState` before any feature sees the event. Twenty features that each parsed the tab list for the area would pay twenty times and drift apart.

A tab list is one event with every row, because it is small and features read it as a whole. A container is an open event followed by one event per changed slot, the way the server sends it. A whole inventory per change would make a capture of an hour's grinding too large to keep.

The sidebar is an event with every line and its styles, not a parsed summary, so that a layer can one day draw the mod's own sidebar in place of the game's from the same state the features read.

## The area lock lives in Game

Each feature used to compare the `Area` row itself, and four features had three different checks: one hid its HUD but kept saving loot from other areas, and one ran wherever the area was unknown. A feature now names an `Area`, and outside it gets only the tab list and a change of world. `FeatureReplayTest` replays every fixture outside its area to prove the feature stays quiet there.

Before the server names an area the player is in none, because a wrong count is saved for good and a late readout costs a quarter of a second. A change of world empties the tab and sidebar state, and each poller posts again even when its rows are the same, so the old lobby's area never outlives the hop and a hop between two identical lobbies does not leave the features deaf.

Some places exist only in the sidebar: the Dungeon Hub's tab list has no info row, and the Void Sepulture is still `Area: The End` in the tab list. So an `Area` is told by a tab row, a sidebar place, or both.

## A big menu is read over several ticks

Reading an item out of Minecraft is the costly part of a menu: a chest of 54 items with long lore took 5.5 ms in one tick. So the poll reads for at most a millisecond a tick, and a big menu arrives over a few ticks. A slot that is still null just after the menu opened may only be unread.

The player's own inventory is the exception, because the HUD shows it after every menu the player closes. Read again from nothing, an inventory of long-lored items took three ticks, and the Inventory Preview emptied and refilled for them. So the poller and `ContainerState` both keep the inventory while another menu is open, and its `ContainerOpened` says `kept`, followed by only the slots whose stack changed. A new world brings a new inventory menu, which is read from nothing.

## Packets that leave no state are posted as they arrive

The tab list and the open container are client state, so the edge polls them. An action bar or an entity's death leaves nothing to poll, and their order against each other carries meaning: the server sends the Combat XP in the action bar right after the death of the mob the player killed, and that pairing is the only sign of whose kill it was. So `ServerPacketsMixin` posts both from the packet handler, with the time each was handled. Polled once a tick, the two would arrive in the poll's order and not the server's.

A block the server placed before its chunk reached the client comes with no update at all, so a marker head that already stands is found by reading each chunk as it loads. Only a head with a skin is posted from there. The End is decorated with plain heads, and one of those posted as a marker would send the Protector's beam to a wall.

## Saved state belongs to the feature, and the disk to the edge

A feature declares one immutable state object and replaces it to change it. The edge loads it at start and writes the changed ones about once a minute, on its own thread, through a temporary file and a rename. A file that fails to parse is moved aside, because a tracker's history is worth more than a clean start.

## Metrics are pulled

`Metrics` adds up timings, counts and failures in memory, and a flush hands one snapshot to each sink. Recording costs the client thread no I/O, and a new receiver is one more sink. Today the release build logs failures, and a running capture writes each snapshot into the file.

The `unparsed` counter matters most. The server is closed and changes without notice, so a feature that stops matching looks the same as a quiet game. A line that reached a feature and failed to parse is the only signal. A feature reports it with `unparsed(text)`, which counts it and, while the player has opted in, hands the text on to the report of unread lines. One call is the only route, so a feature cannot count a failure the report never sees.
