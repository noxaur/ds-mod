# Features

How a feature gets its input and hands back what it shows. The contract an agent writes against is in the [feature skill](../../mod/.claude/skills/feature/SKILL.md).

## A feature holds its parts instead of extending a base

A feature builds the parts it needs and holds them, and two features that share state hold one shared part. Inheritance was rejected: a change to a base reached every class under it, and two siblings could not share state, so two boss features disagreed about whose boss it was. Delegation was rejected too, since it forwards the whole part through the class as inheritance did.

## Features run on events and ticks, never in a frame

A feature handles events and returns what it shows, and the mod rebuilds its readout after an event it could see and four times a second for clocks. The frame only draws what the last rebuild left, so feature code never runs inside a frame, and a feature that is off shows nothing without remembering to hide. It still runs on the client tick, on the thread that draws, so a slow handler delays the next frame, and the beta's perf caps hold every handler's tick together. Calling a UI layer from the feature was rejected, because the replay test could not read it without Minecraft.

## Work too slow for a tick runs on one background thread

Work that takes longer than a frame, such as a Heart of the Dragon plan, runs on one thread of the mod's, and its result comes back at the start of the next tick, as an event would. Running it on the tick was rejected because it drops a frame, and splitting it over many ticks because it turns a plain function into states. In a test it runs in place, so a transcript is the same on every run.

## What a feature says is delivered at the end of the tick

A chat line or an alert a feature sends is held until the tick ends. Printed from inside the handler, it would land above the server line that caused it, because the handler runs before that line reaches the chat. Hiding a server line is the one thing a handler answers on the spot, because the chat asks before it shows the line.

## Shared state is folded once

The tab list, the sidebar and the open container are folded into shared state before any feature sees the event. Twenty features that each parsed the tab list would pay twenty times and drift apart. A container arrives as one event per changed slot, as the server sends it, because a whole inventory per change would make an hour's capture too large to keep.

## The area lock lives in the mod, not the feature

A feature names its area, and the mod gives it events and a readout only there. Each feature checking the area for itself was rejected: four features had three different checks, and one kept saving loot from other areas. Before the server names an area the player is in none, because a wrong count is saved for good, while a late readout costs only a quarter of a second.

## A waypoint shows only in the area it was made in

A waypoint keeps the server's name for its area, from the tab list or the sidebar, and shows only there, because two areas share the same coordinates. The name is the server's own and not one of the areas the mod knows, because players mark places the mod has no feature in. The user chose this over an area per group, as Skyblocker has, and over showing every waypoint everywhere.

## The mod forgets a feature's world and session for it

A feature declares what lasts one world and what lasts one session, and the mod builds both again when they end. Each feature resetting its own fields was rejected: four of them missed one, and a boss feature claimed the next world's boss. A generic state machine per feature was rejected too, because each event would pay for a lookup and the bug was the forgotten reset, not the transitions.

## A big menu is read over several ticks

Reading an item out of Minecraft is the costly part of a menu, and a chest of long-lored items took 5.5 ms in one tick. So the mod reads a menu for at most a millisecond a tick, shared between the reads the server's packets start and the tick's own, and a big menu arrives over a few ticks. The player's own inventory is kept while another menu is open, because the HUD shows it after every menu closes, and reading it again from nothing made the HUD empty and refill.

## Packets that leave no state are posted as they arrive

The tab list is client state, so the edge polls it. An action bar, a death or a chat line leaves nothing to poll, and their order carries meaning: the Combat XP the server shows right after a death is the only sign of whose kill it was. So each is posted as it arrives. A chat line is read before Fabric asks whether it may show, because Fabric stops asking at the first refusal, and a line another mod hid would never reach a feature. A feature still hides a line through that question.

## The open menu is read when the server changes it

Minecraft draws a menu packet on the next frame, and a copy read only on the tick lagged it by up to 50 ms. A key pressed in that gap reached the game as a hotbar swap, or clicked a slot from the page before. So a packet that opens a menu or changes its slots starts a read, which runs after the rest of that batch of packets, since the server sends one packet per slot. A read per packet was rejected, because a burst would compare the whole menu once for each slot. The tick still reads the menu, because the client changes slots itself through predicted clicks, and no packet says so.

## Ping is asked for, and TPS is read from the server's clock

dragsim.co writes 0 for every latency in the tab list, and nothing the client already sends gives a round trip while the player stands still. So the mod sends the game's own ping request once a second, and only while Ping is shown. TPS is the ticks the server's clock advanced over the last ten seconds, because one packet's interval would swing with every network jitter.

## The mod says that a line was hidden, not the feature

A player did not ask for a server line to go missing, so the first time a feature that is on by default hides one, the player is told where its switch is. The mod sends that notice itself. Each feature writing it was rejected, because only prose asked for it and a new feature could skip it.

## An ability's use comes from the server, its length from the lore

Ability Cooldowns keeps no list of abilities. The server names each use, and the item's lore gives the length, so a new weapon is timed with no change to the mod. A list of abilities was rejected, because the server adds weapons without notice. A refusal names no ability, so it goes to the timer nearest the server's count. Matching it by the held item was rejected, because the player may have scrolled since the press.

## A slayer boss is taken as the player's when it comes after their spawn line

dragsim.co names no slayer boss's owner, so the mod tells the player's own by time: the server tells only the owner that a boss is spawning, and the mod treats the first boss to come into view within five seconds as theirs, which another player's boss in that window can fool. Both boss features share that one claim, so they can't disagree.

## Entities reach a feature through one tracker

The mod walks the client's entities once a tick, and each feature declares the types and names it reads. A walk per feature was rejected: each paid for its own walk and carried its own event, redaction and fixture cutting. The walk converts nothing an enabled feature does not match, so with no reader on it costs nothing. Positions stay out of the logic, because an event per move would run every handler for nothing.

## A pet's XP is read from the menu and moved by Combat XP

dragsim.co shows a pet's XP only in the Pets menu, so each read is an exact point, and the Combat XP behind each kill moves the estimate between reads. How much of it reaches the pet is the server's rule, which no capture shows, so the mod learns the ratio from the reads instead of writing a rule down.

## Saved state belongs to the feature, and the disk to the edge

A feature declares one immutable state object and replaces it to change it, and the edge writes the changed ones off the client thread about once a minute. A file that fails to parse is moved aside, not overwritten, because a tracker's history is worth more than a clean start.

## Metrics are pulled, and an unread line is counted

The mod adds up timings and failures in memory and hands a snapshot to each sink, so recording costs the client thread no I/O. A line that reached a feature and failed to parse is counted, because the server changes without notice, and a feature that stopped matching otherwise looks like a quiet game.

## A chat line is read by its head

A player's chat line starts with their name and they can type anything after it, so only a line's start tells the server's lines from theirs. A feature names a line's head and its pattern, and a head that matches with a pattern that does not counts as unread. Each feature matching chat its own way was rejected, because nothing stopped an unanchored match or a failure that went uncounted.
