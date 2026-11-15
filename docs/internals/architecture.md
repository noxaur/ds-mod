# Architecture

ds-mod has three parts. The mod runs in the player's game. The backend is our own REST service, and the website reads from it too. `api/openapi.json` is the contract between them.

## The backend is an Effect API inside a Void app

Void deploys the backend to Cloudflare Workers and owns the database, the file store and the sign-in tables. The API itself is defined and handled with Effect, and one catch-all route hands each request to it. Sign-in is the exception: Void serves `/api/auth/*` with Better Auth before any route of ours, because Better Auth keeps the sessions every other route checks. A route that is not about a session belongs in the Effect API and its contract. The split keeps the way out open. The API takes its store and its caller as arguments, so a move to a single machine needs a new entry point, a new store and a sign-in, and no change to a rule.

The contract is generated from the backend's API definition, because a hand-written one drifts. The mod is Kotlin and can share no types with the backend, so a file in a neutral format is the one thing both sides can hold to.

## The website runs on Vercel, beside the backend

Void deploys no Next.js app, so the website runs on Vercel and calls the backend from its own servers. Nothing in a browser talks to the backend, which keeps the session cookie on the website's domain and leaves the backend no other origin to trust. The cost is that the backend sees the website's few server addresses, not each player's, so Better Auth's per-address rate limit counts every player who logs in on the website together. When website logins fail with 429 under load, give the website a trusted key, as Void's auth guide describes, rather than reading the player's address from a header any client can send.

## The backend looks players up, so dragsim.co is asked once for many clients

The mod and the website both show other players' profiles, and dragsim.co has no key and no stated limit, only an owner's goodwill. So only the backend talks to it, through one `Upstream` service, and it keeps copies in KV: a player's answer is served for two minutes, and kept for a day to answer as `stale` when dragsim.co is down. A lookup by name goes through a lowercase name to UUID entry, so `Name` and `name` share a copy, and a name dragsim.co does not know is remembered for a minute so a typo asked in a loop costs it nothing. The catalog is 2.3 MB, so each isolate also keeps its parse for an hour, and a warm Worker does not parse it again.

A body that no longer decodes is dragsim.co's change and not the client's mistake, so it is logged and treated as unavailable, which a stale copy then covers. Treating it as a defect would answer an empty 500 and hide the stale copy a player could have had. A 404 with a `profile` asked for is told from a missing player by the server's own words, because dragsim.co sends the same status for both.

A profile is answered without its containers, and each answer makes a snapshot of them in KV for ten minutes. Rebuilding every tooltip of thirty storage pages is wasted when a player opens one tab, and fetching a container from a fresh lookup would show a tab from a later moment than the overview beside it. The snapshot id is the only client input that reaches a KV key, so it must have the shape the backend makes. When the player hid their inventory, the items are dropped before anything is written, so neither a snapshot nor the cached copy ever holds them.

The data does not say which hotbar slot is held, so equipment's `held` is the first one, which is a guess. Slayers show the server's level and no progress to the next, because no table of slayer XP is in the catalog.

The profile viewer must be able to show every piece of data dragsim.co sends, so the backend drops none. A new field is added to the contract and never removed, and a key the backend does not map is logged by name on the lookup that saw it, so a new section in dragsim.co's answer shows in the Worker's logs before a player misses it. An item with no known place, such as inventory keys 36 to 40, goes to an `:other` container beside its own, which is listed only when it has items. The sections of plain numbers are read leniently and one at a time, because a section that changes shape should cost only itself. The pet list shows when the player hid their inventory, because pets are not items. Pet levels are null, since neither the data nor the catalog has a pet XP table, and `mode` and `fetched_at` are mapped but not passed on, because the first is the request's own echo and the second is older than the backend's `fetchedAt`.

## A player is their Minecraft account, and the client is never believed

The mod is open source, so anyone can build a client that sends whatever it likes. Sign-in therefore rests on something a client cannot fake. The backend issues a random server id, the client tells Mojang it is joining that server, and the backend asks Mojang who joined. Only the holder of an account's Minecraft session can make that call, so a modified client can sign in as its own account and as no other. No secret ships in the mod, because a secret in an open jar is public.

This proves who is asking and nothing about what they send. A signed-in client can still upload a capture it invented, which is why one request is recorded by several players and an agent compares them. No encryption can close that gap, since the player controls the machine that would hold the key.

Email and password sign-in is switched off, so no account exists that Mojang did not vouch for. A session ends 24 hours after it began and is never extended, so a stolen token dies by itself. The mod pays nothing for that, because it repeats the proof without the player noticing.

The website has no Microsoft login. A site that asked for it would need an Azure app that Mojang approves. Instead the mod, which is already signed in, trades its session for a link that works once within a minute. The token sits in the link's fragment, which a browser sends to no server and leaves out of the Referer, so the website takes it from the page's own script. The page then asks the player to confirm the account before it keeps the session, because anyone can send a player a link to their own account, and a browser signed in to it unnoticed would hand that person whatever the player does next.

## The backend rebuilds item tooltips, because the server's API sends none

dragsim.co's player API sends an item's data and no lore, enchantments or glint. The backend rebuilds the tooltip from the item and the server's `catalog.json`, so the mod draws an item as the game does. The rebuild is a best effort: the catalog lags the live game, it holds no viewer-scaled numbers, and the layout glyphs that right-align a badge or a bar depend on font widths fitted from real tooltips. Tests compare it with tooltips recorded in the game, and a mismatch means the catalog moved, not that the item is wrong. A catalog or an item with odd data draws what it can and never throws.

A glint is not in the data. The catalog's `glowing` wins. Otherwise what players saw decides: an item they always saw glint glints, and one they saw plain, or both ways, does not. Without enough sightings the catalog decides. A few materials glint in the game by themselves, so they glint unless players saw a copy without, whether always or only sometimes.

A skull's texture is a URL the mod fetches, so a texture is used only when its base64 JSON decodes and every URL in it is on `textures.minecraft.net/texture/` with a hex name. Hints come from players, and a texture the check rejects falls through to the next source: the item's own, then the hint for its skin id, then the hint for the item, then the catalog's. Material `player_head` decides a skull, not `skull`, which is false for items such as the Sorrow Helmet that still carry a skin id.

## The mod knows the game only from packets

dragsim.co is someone else's server, closed and custom, so it has no API for ds-mod. Everything the mod knows arrives in the packets that the server sends the client. That is why agents work from captures, which record what the server showed, instead of from a description of the game. The capture format is in [mod/AGENTS.md](../../mod/AGENTS.md#seeing-the-game).

## core is pure, edge touches Minecraft

The `edge` package holds the Fabric callbacks and is the only release code that touches Minecraft. The `core` package holds the logic and imports nothing from Minecraft or Fabric. A test of `core` then runs on a plain JVM, with no game to start. `core-is-pure` in `Rules.kt` keeps the import out.

A feature that only changes how the game draws, such as Fullbright, handles no event, so its code is a mixin in the edge that asks its setting. It still has an empty class in `core`, because the one list of features is what the settings, the docs check and the tests walk. A second list for these would be one more place to forget.

## A capture is a recording of what the features saw

The edge turns what the server sends into a `GameEvent`, and the capture records the events as they were posted. A test replays the recording through the same `Game` the player runs, so nothing stands between a capture and a test that could drift from the real input.

A type of data is either an event or a raw record, and never both. Raw records exist because nobody can design an event for data they have not seen: the recorder writes everything else the server sent, with full fidelity, so the first capture of a screen shows what its event must carry. A feature that needs that data promotes it to an event. Two producers for one type would let a test pass on data the player's game never builds.

Only an excerpt of a capture goes in git, as a feature's fixture, with the player names and their chat replaced. The transcript beside it is the expected result, written as a player would describe it, so a reviewer who knows the game can judge it without reading the code.

## Features run on events and ticks, never in a frame

A feature handles events, and `Game` rebuilds its HUD block and slot marks after an event the feature could see, and four times a second for its clocks. The frame only draws what the last rebuild left. A feature's code then can't cost a frame, however it is written, and its cost is measured in one place.

A feature returns what it shows instead of calling a UI layer. The replay test can then read it without Minecraft, and a feature that is switched off shows nothing without having to remember to hide. `Game` compares each rebuilt readout with the last one, and `Readouts` in the edge hands only a changed one to the layers.

Hiding a server line is the one thing a handler decides on the spot. Fabric asks whether a line may show before it reaches the chat, so `Game.post` returns the answer and the edge posts chat from that question. A line another mod refuses first never reaches the features, because Fabric stops asking at the first refusal.

A chat line or an alert is collected the same way and delivered at the end of the tick. Printed from inside the handler, it would land above the server line that caused it, because the handler runs before that line reaches the chat.

Placement belongs to `Readouts`, which keeps one position per feature id. A feature returns a block and knows nothing about the screen.

`Slots` drops its marks when a container screen closes, and marks handed over earlier may be for the container that was open before. So `Readouts` clears the marks when a container screen opens and hands them over again on the next tick, after the poll has seen the new container. It hands them over again when the screen closes too: closing the player's own inventory changes no container, so no event would bring the hotbar's marks back.

## Shared state is folded once

`Game` folds the tab list, the sidebar and the open container into `TabState`, `SidebarState` and `ContainerState` before any feature sees the event. Twenty features that each parsed the tab list for the area would pay twenty times and drift apart.

The area lock lives in `Game`, not in the features. Each feature used to compare the `Area` row itself, and four features had three different checks: one hid its HUD but kept saving loot from other areas, and one ran wherever the area was unknown. A feature now names an `Area`, and outside it gets only the tab list and a change of world. Before the server names an area the player is in none, because a wrong count is saved for good and a late readout costs a quarter of a second. A change of world empties the tab and sidebar state, and each poller posts again even when its rows are the same, so the old lobby's area never outlives the hop and a hop between two identical lobbies does not leave the features deaf.

The sidebar is an event with every line and its styles, not a parsed summary, so that a layer can one day draw the mod's own sidebar in place of the game's from the same state the features read. Some places exist only there: the Dungeon Hub's tab list has no info row, and the Void Sepulture is still `Area: The End` in the tab list. So an `Area` is told by a tab row, a sidebar place, or both.

A tab list is one event with every row, because it is small and features read it as a whole. A container is an open event followed by one event per changed slot, the way the server sends it. A whole inventory per change would make a capture of an hour's grinding too large to keep.

Reading an item out of Minecraft is the costly part of a menu: a chest of 54 items with long lore took 5.5 ms in one tick. So the poll reads for at most a millisecond a tick, and a big menu arrives over a few ticks. A slot that is still null just after the menu opened may only be unread. The player's own inventory is the exception, because the HUD shows it after every menu the player closes: read again from nothing, an inventory of long-lored items took three ticks, and the Inventory Preview emptied and refilled for them. So the poller and `ContainerState` both keep the inventory while another menu is open, and its `ContainerOpened` says `kept`, followed by only the slots whose stack changed. A new world brings a new inventory menu, which is read from nothing.

## The mod runs only on dragsim.co

Every feature reads lines in dragsim.co's own words, so on another server it can only misread one, and a wrong count is saved for good. The gate lives in `Game`, which treats every feature as switched off while it is closed, so a feature can't forget it, and opening it replays the tab list and sidebar the same way switching one feature back on does. A feature in the edge has no `Game` between it and Minecraft, so it asks `DsModClient.active` itself.

The edge decides once per connection, because Fullbright asks the gate every frame. A player may join by the IP or by a name of their own, so besides the name they typed, the edge compares the machine the connection reached with dragsim.co's addresses. It looks those up at start and again after each join, off the client thread, because a lookup can stall for seconds and the join can't wait for it. A hardcoded IP would stop matching the day the server moves, and nothing would say so. A player who wants the mod elsewhere turns the setting off. The debug build lifts the gate instead, because `runClient` and the game tests run in singleplayer, and a test that had to turn a setting off first would be one more thing to forget. `Game` is open by default for the same reason, so a unit test or a replay never meets the gate.

## Packets that leave no state are posted as they arrive

The tab list and the open container are client state, so the edge polls them. An action bar or an entity's death leaves nothing to poll, and their order against each other carries meaning: the server sends the Combat XP in the action bar right after the death of the mob the player killed, and that pairing is the only sign of whose kill it was. So `ServerPacketsMixin` posts both from the packet handler, with the time each was handled. Polled once a tick, the two would arrive in the poll's order and not the server's.

A block the server placed before its chunk reached the client comes with no update at all, so a marker head that already stands is found by reading each chunk as it loads. Only a head with a skin is posted from there. The End is decorated with plain heads, and one of those posted as a marker would send the Protector's beam to a wall.

## Saved state belongs to the feature, and the disk to the edge

A feature declares one immutable state object and replaces it to change it. The edge loads it at start and writes the changed ones about once a minute, on its own thread, through a temporary file and a rename. A file that fails to parse is moved aside, because a tracker's history is worth more than a clean start.

MoulConfig has a data file class, and ds-mod does not use it: it writes on the caller's thread, and after a failed load its next save overwrites the bad file.

## Metrics are pulled

`Metrics` adds up timings, counts and failures in memory, and a flush hands one snapshot to each sink. Recording costs the client thread no I/O, and a new receiver is one more sink. Today the release build logs failures, and a running capture writes each snapshot into the file.

The `unparsed` counter matters most. The server is closed and changes without notice, so a feature that stops matching looks the same as a quiet game. A line that reached a feature and failed to parse is the only signal. A feature reports it with `unparsed(text)`, which counts it and, while the player has opted in, hands the text on to the report of unread lines. One call is the only route, so a feature cannot count a failure the report never sees.

## The recorder is in the release jar, and runs only for a contribution

Developers' accounts cannot reach all of the server's content, so players record it for them. That needs the recorder in the jar a player installs, though it reads private client fields and records everything, chat included. Three things make that safe. It starts only from a contribution the player accepted, or from the debug build's `/dscapture` and `-Pbrief`, so while none runs every hook is one null check and the recorder costs a frame nothing. The file never leaves the machine as recorded: the mod redacts it first (below). And the recorder only ever reads what the server already sent the client, so it cannot send for the player.

The rest of the developer tools stay in `src/debug`, a separate source set that `src/main` can't see, so the compiler keeps them out of the release jar. They are `/dscapture`, `/dskit`, `cutFixture`, the window title that names a `-Pbrief` and the title screen's Join DragSim button. The debug jar holds the release jar inside it, so the release code runs the same in both.

The recorder reads private fields, which needs an access widener, and a mod declares only one. So the jar's file is ours followed by MoulConfig's lines, merged by the build.

Recording is client-thread work that no thread can do for it, because it reads game state. Writing, redacting, compressing and uploading happen on other threads, and the writer opens the file on its own thread, so starting a recording touches no disk on the client thread.

A recording reads every item and name in view four times a second, and Minecraft's codec was most of what that cost: over a millisecond a pass with a full inventory and a crowd of armored mobs. Almost none of them change between passes, so the session encodes a value only when it differs from the copy it kept, and `PerfTest` caps both passes.

## A contribution is a brief run that ends in an upload

A request is a brief plus requirements, which an agent publishes to the backend. The mod offers it to a player who qualifies, and the same `BriefRun` that `-Pbrief` uses walks them through it. A request has a time limit measured from the moment the player accepts, not from their first keypress: a run that waits for a key would otherwise sit on the screen forever once the player walked away. A run that outlasts it, is cancelled, or loses its connection is discarded and not uploaded, because a recording the player abandoned halfway is the likeliest to be wrong. A capture whose writer lost a record or did not finish is discarded the same way, since an agent would read the part as the whole. A change of world is not a disconnect, because a brief's warps change the world on one connection.

The run's life crosses the client thread, the io thread, the backend's thread and the disk, which outlives a quit or a crash, so `specs/Contribute.tla` models it. Three decisions come from it. An upload claims its file, and a resend skips claimed files, because a resend that listed a file an upload was still sending would send it twice and fail on the deleted file. The redactor writes a `.part` and renames it, so only a whole, redacted `.gz` is ever kept or sent, and the next start deletes anything else in `dsmod/contributions/`, which is a raw capture or a half redaction. And the offers are fetched only after that sweep, so the sweep can never delete a capture a run has started.

A recording that fails to upload is kept as a redacted `.gz` named for its request, and `/dsmod submit` sends what is there. One the submissions route refuses for good (404, 409 or 413) is deleted, since sending it again would fail again. Any other failure, an empty 400 or a failed sign-in included, keeps it. Quitting waits a few seconds for an upload in progress, and one that does not finish stays for the next `/dsmod submit`.

The mod checks a request's requirements itself, because the backend cannot know what a player has seen. Today the only kind is an area, met when the tab list's `Area` row or the sidebar's `⏣` place has ever named it, so the places seen are saved. These are the server's names and not an `Area`, because a request is most often for a place the mod has no feature in yet. A kind the mod does not know is never offered, so an older mod cannot offer what it cannot judge.

Skipping tells the backend, which hides the request from that player and nobody else.

## An item sighting is what the player's own inventory shows, agreed on by two players

dragsim.co's API sends an item's data but no lore, enchantments or glint, and only some skull textures, so the backend learns how items really look from players. A sighting goes out under Report Game Data, the one switch for everything the mod reports for its developers. A switch per kind would ask the player again, and a player who read the first prompt would have agreed to one kind only, so each new kind is a new consent. One general switch is asked once, and the docs say what goes today. It shares one batch and backoff (`Batch`), and nothing is read while it is off. A batch the backend refuses for good (400, 404, 409, 413, 422) is dropped, because sending it again can only fail again, and any other failure is tried at the next report.

- Only the player's own inventory, never a menu's slots. A menu can show another player's items, such as an auction's seller or a trade, and the redactor cannot find a name it was never told. The inventory is read from the tick, and a slot is looked at again only when its stack changes. A look the session has seen costs a few component reads, because the item is built only for a new one.
- The backend counts distinct players and acts on a look only when at least two agree, because any signed-in client can send anything, and one client sending a thousand times is still one. A player may hold at most 2,000 item rows, so one account cannot fill the table before honest players arrive. The 20,000-row table cap is only a backstop against many accounts. The caps are soft against one account sending many reports at the same instant: D1 cannot hold a transaction across the count and the insert, so a burst can pass the cap once before it is refused, and the rows it adds cannot sway a hint, which still needs a second player. Textures are refused unless every url is on `textures.minecraft.net`, so a client cannot make the backend or an agent's browser fetch what it chose.
- `custom_data` goes through an allowlist, not a blocklist, and inside `item_data` and `extra` too: the server keeps copy ids, owners and more there, and a key it adds later would otherwise leave the machine unreviewed. A head goes as its skin's address alone, rebuilt from the item's `skin`, because the server's `textures` property names its player beside the skin. The capture redactor drops skins, so a sighting takes it from the item before redaction.

## A capture is redacted by rules that say where a name sits

A capture holds player names and private chat, and a fixture cut from it goes into a public repository. `Redactor` rewrites a capture so it holds neither. `PlayerNames` is the one place that knows where the server puts a name, so the names `cutFixture` replaces everywhere are the names `FixtureLeakTest` looks for. The test cannot see typed text or a name kept to where a rule found it, which is why a fixture is still read before it is kept. Both live in `core` rather than the debug build, so that a release build can redact a capture as well.

A redactor fails in two directions. A name it misses leaves the machine, and a word it takes for a name changes what the server sent, so a fixture replays a line the server never wrote. Each rule therefore says where in a text a name sits, and a name is always replaced there. A name a rule found is also replaced wherever else it stands, because names turn up where no rule looks, such as a head's item name, unless that would rewrite the server's own words. Two kinds are kept to the places a rule found them. A plain name on a player entity may be an NPC's, since the server's NPCs are player entities, some with plain names such as `Dungeons`. And a player can be named what the server calls itself or a place, as a staff account is named after the server, so a name that the sidebar's title, its `⏣` place, the tab list's area row or its header also shows is not replaced across the capture. A player named after a game word the server writes only in other places, such as `Eye`, still has it replaced everywhere.

What a player typed is found by shape, not by a list of formats. The server's whisper, party and guild lines are in no capture, so a list would be a guess that passes its own tests. The shape is a known name followed by a `: ` on the same line, which the server never writes in its own lines; across every capture so far, no server line lost text to it. It costs the text of a server line that names a player and then has a `: `, and of a message that starts like a speaker's, `Word: `, without a tag such as `[NPC]`. And a message from a player no line of the capture names keeps both the name and the text.

Typed text is found only in a `system` event, so a raw `chat` record, whose event carries the same line, is dropped, and so is an older capture's `system` line that does not read as today's event. A component that spells a name across several pieces, one per color, is replaced by its redacted plain text, because no single string holds the name to replace, and an older raw text that names someone loses its `§` codes for the same reason.

The same holds for the lines the mod reports when a feature could not read them: the player opts in, names are replaced, and the backend keeps only one row per kind of line.

## The mod signs in when it first needs to, and keeps the session in memory

Nothing contacts the backend until the player opens Contribute, uses `/dsmod login`, or has turned on the report of unread lines. The session token is held in memory and never written, logged, shown or recorded: the jar is public, and a token on disk or in a stream is a stolen account for the day it lives. For the same reason `/dsmod login` opens the browser directly, and the login token never appears in chat, where a viewer could use it first. Minecraft's own opener logs the address when it fails, so the mod uses the system's opener and reports a failure without the address.

A session ends after 24 hours, so a 401 signs in again and repeats the call once. A second 401 is an error, not a loop.

The backend's address and the website's are set when the jar is built, in `gradle.properties`, and written into the jar's metadata. They are not a player's setting, because a player who pointed the mod somewhere else would be sending their captures there.

## The release jar carries its own MoulConfig

Settings use MoulConfig, the settings screen SkyHanni uses. MoulConfig ships inside the release jar, relocated under `io.github.noxaur.dsmod.deps`, so ds-mod can't clash with another mod that carries a different MoulConfig. A MoulConfig type in a public signature therefore breaks any code compiled outside the jar, such as the game tests, which is why `DsModClient` exposes `DsModConfig` and not the `ManagedConfig` that holds it. `runProdClientGameTest` loads the built jars the way a player does, because a packaging mistake such as a missing access widener passes every unit test.

## Specs for interleavings

A state machine whose states interleave in ways a unit test can't enumerate gets a TLA+ spec in `specs/`. The mod's Gradle build runs TLC on every spec, so the spec and the code are checked in the same run. A parser or an overlay gets a test instead.

## A feature states a role, the text layer owns the color

Minecraft mods usually write color codes inline, and their colors drift from feature to feature. In ds-mod a feature says what a piece of text means (body, value, muted and so on) and `core.text` maps that role to a color. The palette then lives in one file, and `text-by-role` in `Rules.kt` fails the build on a color code or a raw color anywhere else in the release code.

Text the server sent is a separate type, `ServerText`, which carries whatever color the server chose. A feature reads one and builds the other, so the server's colors cannot leak into what the mod shows.

## UI layers keep what they are given

Each surface (chat, HUD, alerts, container slots, tooltips, world marks) has one object in `edge` that draws a plain model from `core`. A model is handed over when something changes, and the layer converts it to Minecraft's types once and redraws it from then on. A feature that built its text every frame would allocate in the render loop, where a dropped frame is a bug, so the layers offer no per-frame hook.

## An item on the HUD is drawn from the mod's own record of it

dragsim.co's armor is a player head with a skin and leather with a dye, and its items have their own tooltip frames, so an `Item` keeps how the item looks as well as what features read: the skin, the dye, the glint override and the tooltip style. `Hud` rebuilds a stack from the `Item` alone. An earlier version asked the poller for the very stack an item was read from, but the poller lets go of a stack once its menu closes or another pet comes nearer, and the icon then fell back to a Steve head and brown leather. The Pets menu, which closes before its pet is drawn, could not have been drawn that way at all. The skin is kept as the last part of its `textures.minecraft.net` address, without the server's signature or the uploader's name the property holds. The game draws an unsigned skin on a head, as it does for a head a command gives, and the redactor drops the skin as it drops every other.

## Chrome is drawn in code, from a theme

Panels, buttons, slots and bars are a few filled rectangles each, drawn by `Kit` in the colors of a `Theme`. Minecraft's own screens stretch nine-slice textures instead, and ds-mod does not: a second look would need a second set of textures drawn by hand, and a new shape would need an image editor. In code a theme is one list of colors and a shape is a function, and `draw-with-kit` in `Rules.kt` keeps every other file from drawing its own. Only the icons are textures, white so that one sprite takes any color. The one exception is the Hotbar look of a HUD block, which cuts its slots from the game's own hotbar sprite: the point of it is to match the player's hotbar, and only the sprite follows their resource pack. The slot is the one shape drawn dozens of times a frame, so `Kit` paints it into a texture from the theme's colors once per theme and blits it. Five fills per slot made a themed inventory cost 0.2 ms a frame. The paint runs at the end of a client tick: made while a frame was being built, the texture broke the drawing of every frame after it.

Two themes ship, because the references players know disagree: resource-pack screens are dark, and Minecraft's own are light gray. A light panel can't carry the text layer's colors, which were picked for chat and the HUD and wash out on gray. So a label on a panel is not role text. It is a plain string in the theme's ink, and role text sits only on a well or the HUD's plate, which stay dark in every theme. A theme that made those light would break every colored row.

## The game's menus are redrawn, not retextured

Themed Menus draws a menu with the kit instead of recoloring its texture, for the reason the kit exists: one theme is a list of colors, and a texture per theme would be art to keep by hand. The mixins wrap only the call that blits the menu's texture. The rest of the screen still runs, so the inventory still draws the player, and Fabric's background event still fires for the slot marks. A screen whose texture holds more than a panel and slots, such as a furnace's flame or an anvil's field, is left alone. Drawing those in the kit is work for a screen the server does not open. The recipe book's button is drawn by the kit where the game draws it, not replaced by a widget of the mod's, because the game moves that button itself when the book opens. The server's own Go Back, Close and page arrows are drawn as kit buttons in their slots for the same reason Back Buttons draws one: a menu that mixes item buttons with the mod's reads as two systems. Only the drawing changes, so the click is still the player's own on the server's slot, and the server's tooltip stays. The server marks them with nothing but an item and a name, and it spells "Next page" in storage and "Next Page" in the auction house, so `MenuButton` lists each name a capture has shown.

A tooltip with its own `tooltip_style` keeps it. dragsim.co gives every item a style per rarity from its resource pack, so the kit's backing reaches only plain tooltips. Replacing all of them would erase the rarity frames the server draws.

Glass is a look of Themed Menus and not a feature of its own, so the player finds everything about how a menu looks in one place, and a menu the kit cannot draw, such as a furnace, never turns half glass. Glass is the HUD plate's dark fill at the player's alpha in both themes, not the theme's panel made see-through. Vanilla's light gray panel over a bright sky would leave its dark labels unreadable, while a dark sheet takes the light ink in either theme. A menu opened in the world dims the world with one gradient and no blur, and glass skips only that gradient, so every other screen keeps its dimming. The HUD under a glass menu shows through it as the world does, hearts and hotbar included, because seeing what is behind is the point of glass. Only the crosshair is hidden, since it would sit in the middle of the menu as a stray mark. The buttons the kit draws on a menu, the server's and the mod's alike, turn to glass with it, so a glass menu does not carry opaque buttons from the theme. Glass tabs stand taller rather than join the panel: two see-through fills laid on each other show a darker band where they meet.

Stable Menus keeps a menu's bottom edge where a six-row menu's sits, not its top, because the server puts Go Back and Close in the bottom row of most menus and the player's inventory sits under it. Storage pages and the Ender Chest are the exception: they hold the player's items in every row below the top, so their Go Back, Close and page arrows sit in the top row. A chest menu's texture recenters itself on the screen and ignores the menu's top, so `ChestScreenMixin` moves the texture by the same amount. Without that the slots would move and the art would not.

Hide Filler only skips drawing. A click on a hidden pane still goes to the server, as the `no-automation` rule asks of any feature DragSim Utils lacks.

The server closes a menu and opens the next in the same tick. Between the two the game has no screen, so it takes the mouse and then lets it go at the middle of the window. Keep Cursor puts the cursor back when a screen opens within half a second of the last one closing. It cannot tell a server's close from the player's, and does not need to: a player who reopens a menu that fast wants the cursor where it was too.

Back Buttons can only send a command. Reopening a menu by replaying the clicks that reached it would be a macro, and a menu from an NPC needs a click on the NPC, so a menu with no command earlier in its run gets no button. It resends the player's own command, unchanged, on the player's click. That makes it DragSim Utils' Menu Button with the command filled in, which is the exception it takes to `no-automation`. The button covers Go Back's slot only while that slot holds filler. The server fills every slot it leaves unused, so an empty slot is one the player can put an item in, as each slot of a storage page is, and a button over it would take the click meant for the item. A command sent from inside a menu, as Menu Button sends one, starts the run again, so Back leads to the newest command's menu.

Better UI's switch covers only the controls the mod adds to menus, not how the menus look, because a player who wants the vanilla controls back may still want dark, steady menus. Menu Tabs are places along a menu's top and bottom edges, as creative tabs and the Skyblocker and SkyOcean tabs players know, so a tab dropped on another trades places with it instead of pushing a row along. The bottom edge stays put under Stable Menus and the top follows the menu's height. Both run off a short screen at a large GUI scale. The search bar opens along its tab's edge toward the menu's middle and hides the tabs it covers, so a click never lands on a tab under it. While the bar has focus it takes every key, because the menu's own keys would act on a typed letter: E closes it, Q drops an item and a digit swaps one. Command tabs send commands the player set up, on a click, which is DragSim Utils' Command Binds on a button and the exception they take to `no-automation`. The tab whose command opened the menu shown now is the selected one, which `MenuTrail` knows without matching the menu's title. Opening the recipe book moves the inventory and the crafting table sideways without laying the screen out again, so the tabs follow the menu's left edge on every frame instead of keeping where `init` put them.

## Rarity marks are baked from one color

A rarity style has glows that change opacity pixel by pixel, which as fills would be a few hundred rectangles per slot and thousands per chest, every frame. So `SlotArt` in `core` paints each rarity's 18x18 once, from the rarity's color alone, and `SlotArts` keeps one atlas per surface that it paints again only when the style changes. A marked slot then costs one blit, and the slots of a chest batch into one draw because they share the texture.

The art depends on the slot under it: a faint tint vanishes on Minecraft's gray and a dim one sinks into a dark resource pack's black. The mod can't see the pixels under a slot, so it reads the chest texture's slot from the selected resource packs when a container screen opens and the packs have changed. The hotbar lies over the world, so its art is always the dark kind.

Fabric attaches HUD elements before or after the whole hotbar, so the art behind the hotbar's items comes from `HudHotbarMixin`, which runs as vanilla is about to draw each item, and the gems come after it as a HUD element.

## The HUD's scale snaps to screen pixels

The kit's edges are one pixel wide, and the HUD is the one place a player scales freely. At a scale of 1.3 a one-pixel edge covers 2.6 screen pixels at GUI scale 2, so some edges come out two pixels wide and others three, and the frame looks broken. `Readouts` therefore rounds a block's scale, times the HUD's, to a whole number of screen pixels per HUD pixel. The editor steps by one screen pixel for the same reason: a smaller step would change the saved number and draw nothing new.

A block's saved place is the corner of its plate, so a block stays where the player dropped it whether the plate is shown or not.

## World marks reach Minecraft once per tick

`World` draws through Minecraft's gizmos, the shapes its own debug views use. It submits them at the end of each client tick, and Minecraft replays the tick's gizmos on every frame until the next one. Submitting from Fabric's per-frame gizmo event drew nothing in the game tests, and would do the work every frame instead of twenty times a second.

## Free Look takes the mouse before the player does

The player's rotation is what the game aims with and what it sends to the server. Free Look must leave it alone, or every look around would turn the head other players see and move the crosshair's target. So `MouseTurnMixin` stops the turn before it reaches the player and `Cameras` spends it on an angle of its own, which `CameraMixin` gives only to the camera. Setting the rotation and putting it back afterwards would send the turn in between.

Camera's height and tilt are applied after the game has put the camera at the eyes and before it backs the camera out. The game's own wall check then runs from the lifted start along the tilted angle, so neither setting can push the view through a block.

The key's press comes from `KeyMapping.click`, the place the game counts presses for its own key binds, and its release is read from the key's state each frame. A press read from the state would miss a tap that starts and ends inside one frame, and Toggle mode is nothing but taps.

## A command bind sees every key, and fires only in the world or a menu

A bind of keys held together needs every press and release, wherever they happen, or a Shift held before a menu opened would be missed. So `KeyInputMixin` and `MouseInputMixin` hand each one to `CommandKeys` before the game acts, and `BindMatcher` in `core` holds the keys that are down. Whether a press may fire is a separate question, asked at the press: in the world, or in a container menu with no text field focused, and never with F3 held. `KeyMapping.click` would not do, because the game calls it only with no screen open.

GLFW repeats a held key as its own action, and a repeat is not a press. A release can go unseen, as when the window loses focus, so each tick forgets keys that are no longer down.

A sequence fires as soon as no longer sequence could still follow, so a bind of F7 then F8 needs no wait unless another bind starts with F7, F8. Otherwise it waits for the gap from the settings, as SkyOcean does.

The settings ring a key that the game uses too. The game's debug keys work only with F3 held, so F3 is the only one of them that counts, and F6, which the game binds to F3+F6, is free.

## A locked slot refuses the click before the game predicts it

The client moves the item in its own inventory as it sends a click, so a click dropped at the packet would leave the player looking at a move the server never made. Every pick up, Shift click, number key, throw and double click in a menu passes through `AbstractContainerScreen.slotClicked` first, so `SlotClickMixin` refuses there and nothing is predicted or sent. The drop key outside a menu never reaches a screen, which is why `DropKeyMixin` exists beside it.

A lock is an index in the player's own inventory. A menu numbers its slots its own way, and the same inventory slot has another number in a chest than in the inventory screen, so a lock kept by menu index would move to another slot with each menu.

An item lock is the ID the server writes in `item_data.uuid`. In the captures every item that does not stack has one and no stackable item does, so two swords of one kind are told apart and a stack cannot be locked this way. Reading it copies the item's whole data tag, so `SlotGuards` keeps each ID by the data component, which stacks share.

A Shift click lands wherever the menu's own code finds room, and each menu orders that its own way. `SlotGuards` only asks whether a locked slot could take the item, and when one could, it carries the item to a slot that is not locked with the two clicks a player would make. That needs the menu's rough order and never its exact one.

The swap-hands key is not guarded. On dragsim.co it opens the server's menu and swaps nothing.

## Private fields are read through accessor mixins

Minecraft keeps an open container's position and size on screen in protected fields, and Fabric's screen events fire outside the transform that places the slots. `ContainerScreenAccessor` reads the four fields, and the slot under the mouse. `ChatComponentAccessor` does the same for the chat's messages and wrapped lines, which Copy Chat needs to tell what a press landed on.

## CI measures the mod in a real game

"Fast and smooth" is checked, not promised. `PerfTest` boots the jars a player installs and reads the mod's own `Metrics` timers, so the numbers are the ones a capture records in play. A unit benchmark would miss the draw cost, which exists only inside Minecraft's renderer.

Runners differ in speed, and one runner differs from run to run. `PerfTest` times a fixed loop on the machine it runs on and scales every time to the device named in `perf-budgets.json`, so a cap means the same on a laptop and on a runner. Each cap gates the median of twelve windows, because one window on a shared runner can be several times slower than the rest.

The game takes about 40 seconds to boot on a runner, where it renders in software. So CI runs `PerfTest` alone, and the other game tests run before a beta release. A broken screen can therefore reach `main`, and it is caught within two days, before any player gets it.

## A game test runs behind the developer

Agents run the game tests while a person works on the same machine, often with their own game open. So the test game opens without the keyboard, under the window class `dsmod-gametest`, and writes its sound to a file. All of it lives in the `gametest` source set, so `runClient` and the release jar are untouched.

The window class is what lets a window manager tell the test from the game the developer plays, which has the same title. On Hyprland the test installs its own named window rule through `hyprctl` and needs nothing in the developer's config. A rule in a dotfile would be one more setup step, and the core works with no setup.

Both settings must be made from inside the game, just before it creates its window. A GLFW window hint lasts only until the next window, and Minecraft resets the hints first, so a launch flag can't carry it. OpenAL reads `ALSOFT_CONF` from the process environment when the first device opens, which a Java system property can't set, and Loom's production run task takes no environment. `wave` is the only driver listed, so when it fails the game is silent instead of falling back to the speakers.

## CI's time limit is a step, not a timeout

`timeout-minutes` cancels a job and says only that it was cancelled. It also can't tell a slow change from a cold cache: the first run after a version bump downloads Minecraft and takes about four minutes whatever the change does. The `time-limit` action runs last, reads when the job started, and fails with the time and what to do about it. It lets a run through when the cache was cold.
