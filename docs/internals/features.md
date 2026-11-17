# Features

How a feature gets its input and hands back what it shows. The contract an agent writes against is in the [`feature` skill](../../mod/.claude/skills/feature/SKILL.md).

## Features run on events and ticks, never in a frame

A feature handles events, and `Game` rebuilds its HUD block and slot marks after an event the feature could see, and four times a second for its clocks. The frame only draws what the last rebuild left. A feature's code then can't cost a frame, however it is written, and its cost is measured in one place.

A feature returns what it shows instead of calling a UI layer. The replay test can then read it without Minecraft, and a feature that is switched off shows nothing without having to remember to hide. `Game` compares each rebuilt readout with the last one, and `Readouts` in the edge hands only a changed one to the layers.

A feature's HUD block is built with `block { }`, which heads it with the feature's title. A feature can't then retype or misname itself, and the header is built once.

Hiding a server line, or hiding or replacing a floating text, is the one thing a handler decides on the spot. Fabric asks whether a line may show before it reaches the chat, so `Game.post` returns the answer and the edge posts chat from that question. A line another mod refuses first never reaches the features, because Fabric stops asking at the first refusal.

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

Leaving the server changes no world, since the game drops its world without entering another, so `LeftServer` empties the same state and is where a feature forgets what one session holds, such as the pet that a change of world keeps. A feature that was off when the player left hears it as it comes back on, before the tab list and the sidebar, because a dungeon's sidebar names no pet and would leave the old one standing. Fabric reports the disconnect on the network thread, and the game drops every task queued for its own thread while it disconnects, so a flag carries it to the next client tick instead of `Minecraft.execute`.

Some places exist only in the sidebar: the Dungeon Hub's tab list has no info row, and the Void Sepulture is still `Area: The End` in the tab list. So an `Area` is told by a tab row, a sidebar place, or both.

## A big menu is read over several ticks

Reading an item out of Minecraft is the costly part of a menu: a chest of 54 items with long lore took 5.5 ms in one tick. So the poll reads for at most a millisecond a tick, and a big menu arrives over a few ticks. A slot that is still null just after the menu opened may only be unread.

The player's own inventory is the exception, because the HUD shows it after every menu the player closes. Read again from nothing, an inventory of long-lored items took three ticks, and the Inventory HUD emptied and refilled for them. So the poller and `ContainerState` both keep the inventory while another menu is open, and its `ContainerOpened` says `kept`, followed by only the slots whose stack changed. A new world brings a new inventory menu, which is read from nothing.

## Packets that leave no state are posted as they arrive

The tab list and the open container are client state, so the edge polls them. An action bar or an entity's death leaves nothing to poll, and their order against each other carries meaning: the server sends the Combat XP in the action bar right after the death of the mob the player killed, and that pairing is the only sign of whose kill it was. So `ServerPacketsMixin` posts both from the packet handler, with the time each was handled. Polled once a tick, the two would arrive in the poll's order and not the server's.

A block the server placed before its chunk reached the client comes with no update at all, so a marker head that already stands is found by reading each chunk as it loads. A head is posted only when its skull has a skin, whether it came with the chunk or as a block update. The End is decorated with plain heads, and one of those posted as a marker would send the Protector's beam to a wall.

## Ping is asked for, and TPS is read from the server's clock

dragsim.co writes 0 for every player's latency in the tab list, so the tab list cannot give Performance Stats a ping. Nothing the client already sends and receives gives a round trip while the player stands still: the client answers keep-alives and the server's own pings without seeing the other leg, and a block or use acknowledgement comes back only when the player acts. So `Pings` sends vanilla's ping request, the one the F3 network chart sends, once a second, and only while Ping is shown on dragsim.co, and it is the one place the mod does. The answers arrive as `PingMeasured`, the chart's own included.

The server sends its world's tick count about once a second, and a server that lags sends it late. The ticks over the time between the oldest and the newest count of the last ten seconds give its rate. One packet's interval alone would swing with every jitter in the network. A server that stops ticking stops sending, so a window that ended at the newest count would keep showing full speed through a freeze, and once the newest is three seconds old the wait counts as time with no ticks.

## Game says that a line was hidden, not the feature

A player did not ask for a server line to go missing, so the first time a feature that is on by default hides one, the player is told where its switch is. Each feature used to write this notice itself, and only prose asked for it, which a new feature could skip. `Game` now sends it from `hide()`, with the switch's name and default from the feature and its section from `SettingsTree.home`, and keeps who has told in `hidden.json`. A feature the player turned on hides what they asked it to, so it says nothing.

## An ability's use comes from the server, its length from the lore

Ability Cooldowns keeps no list of abilities. The server names each use, in the action bar's `-100 Mana (Giant's Slam)` or a chat line, and the `Cooldown` line under that name's heading in the lore of an item the player carries gives the length, so a new weapon is timed with no change here. The mana part stays up through a few of the server's half-second updates, so a use is the part arriving, never each repeat. Only the player's own slots are read: in a menu those are its last four rows, and a profile menu's upper rows hold another player's items.

The refusal `This ability is on cooldown for 3.35s` names no ability, so it goes to the timer whose end lies nearest the server's, and two that close are left alone. Matching by the held item was rejected because the refusal answers a press a round trip earlier, and the player may have scrolled since. The timers run on the wall clock, while the server counts its own ticks and falls behind when it lags, so a timer can read Ready a little early. Each refusal puts the timer back on the server's count.

Riftwalker's blink is the one cooldown the server shows nothing of, so it starts on `Hit`, the player's own attack as the client sends it. `Hit` carries only the entity's type, because the charge needs nothing more, and a hit while the bonus charges does not restart it.

## A pet's XP is read from the menu and moved by Combat XP

dragsim.co shows a pet's XP only on its item in the Pets menu, so Active Pet takes each read as an exact point in time, and two reads at one level give the rate. Between reads the only thing that moves is the Combat XP the action bar shows behind each kill, and how much of it reaches the pet is the server's rule, which no capture shows. So the rule is not written down: the reads learn the pet XP per Combat XP, and each read puts the live estimate back on the server's number. Only a pet whose footer says `COMBAT PET` is moved this way, since another kind gains from another skill. A Combat XP line counts only right behind a death, as in Zealot Tracker, because the server repeats the last one for about a second.

The Pets menu sends no `custom_data` on its pet items, while the Auction House sends each pet's `item_uuid`. So Active Pet keys what it remembers by that uuid when an item has one and by the pet's name when not, and two pets of one name share what the last menu read showed of either.

## Saved state belongs to the feature, and the disk to the edge

A feature declares one immutable state object and replaces it to change it. The edge loads it at start and writes the changed ones about once a minute, on its own thread, through a temporary file and a rename. A file that fails to parse is moved aside, because a tracker's history is worth more than a clean start.

## Metrics are pulled

`Metrics` adds up timings, counts and failures in memory, and a flush hands one snapshot to each sink. Recording costs the client thread no I/O, and a new receiver is one more sink. Today the release build logs failures, and a running capture writes each snapshot into the file.

The `unparsed` counter matters most. The server is closed and changes without notice, so a feature that stops matching looks the same as a quiet game. A line that reached a feature and failed to parse is the only signal. A feature reports it with `unparsed(text)`, which counts it and, while the player has opted in, hands the text on to the report of unread lines. One call is the only route, so a feature cannot count a failure the report never sees.

## A chat line is read by its head

A player's chat line starts with their name, and they can type anything after it, so only a line's start tells the server's lines from theirs. A feature reads chat through `line(head, pattern)`, which matches the head at the start and calls `unparsed` itself when the head matches and the pattern does not. Each feature used to read `plain` in its own handler, with `startsWith`, `in` or a regex, and nothing stopped an unanchored match or a branch that forgot `unparsed`. A dragon's loot rows count only within a second of their header, because a head handler sees no line but its own and so cannot end the rows at the first other line.
