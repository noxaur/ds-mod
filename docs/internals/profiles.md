# Profiles

How the profile viewer gets another player's profile and draws its items as the game would.

## The backend looks players up, so dragsim.co is asked once for many clients

The mod and the website both show other players' profiles, and dragsim.co has no key and no stated limit, only an owner's goodwill. So only the backend talks to it, through one `Upstream` service, and it keeps copies in KV: a player's answer is served for two minutes, and kept for a day to answer as `stale` when dragsim.co is down. A lookup by name goes through a lowercase name to UUID entry, so `Name` and `name` share a copy, and a name dragsim.co does not know is remembered for a minute so a typo asked in a loop costs it nothing. The catalog is 2.3 MB, so each isolate also keeps its parse for an hour, and a warm Worker does not parse it again.

A body that no longer decodes is dragsim.co's change and not the client's mistake, so it is logged and treated as unavailable, which a stale copy then covers. Treating it as a defect would answer an empty 500 and hide the stale copy a player could have had. Only the server's own words make a 404 a missing player, because dragsim.co sends the same status for a missing profile, and a 404 from a moved route would otherwise remember a miss and hide the stale copy. Without them, a 404 with a `profile` asked for is a missing profile, and one without is unavailable.

## Containers come from a snapshot, not a fresh lookup

A profile is answered without its containers, and each answer makes a snapshot of them in KV for ten minutes. Rebuilding every tooltip of thirty storage pages is wasted when a player opens one tab, and fetching a container from a fresh lookup would show a tab from a later moment than the overview beside it. The snapshot id is the only client input that reaches a KV key, so it must have the shape the backend makes. When the player hid their inventory, the items are dropped before anything is written, so neither a snapshot nor the cached copy ever holds them.

## The backend drops nothing dragsim.co sends

The profile viewer must be able to show every piece of data dragsim.co sends. A new field is added to the contract and never removed, and a key the backend does not map is logged by name on the lookup that saw it, so a new section in dragsim.co's answer shows in the Worker's logs before a player misses it. An item with no known place, such as inventory keys 36 to 40, goes to an `:other` container beside its own, which is listed only when it has items. The sections of plain numbers are read leniently and one at a time, because a section that changes shape should cost only itself.

Some gaps are the data's, not the backend's:

- The data does not say which hotbar slot is held, so equipment's `held` is the first one, which is a guess.
- Slayers show the server's level and no progress to the next, because no table of slayer XP is in the catalog.
- Pet levels are null, since neither the data nor the catalog has a pet XP table. The pet list shows when the player hid their inventory, because pets are not items.
- `mode` and `fetched_at` are mapped but not passed on, because the first is the request's own echo and the second is older than the backend's `fetchedAt`.

## The backend rebuilds item tooltips, because the server's API sends none

dragsim.co's player API sends an item's data and no lore, enchantments or glint. The backend rebuilds the tooltip from the item and the server's `catalog.json`, so the mod draws an item as the game does. The rebuild is a best effort: the catalog lags the live game, it holds no viewer-scaled numbers, and the layout glyphs that right-align a badge or a bar depend on font widths fitted from real tooltips. Tests compare it with tooltips recorded in the game, and a mismatch means the catalog moved, not that the item is wrong. A catalog or an item with odd data draws what it can and never throws.

A glint is not in the data. The catalog's `glowing` wins. Otherwise what players saw decides: an item they always saw glint glints, and one they saw plain, or both ways, does not. Without enough sightings the catalog decides. A few materials glint in the game by themselves, so they glint unless players saw a copy without, whether always or only sometimes.

A skull's texture is a URL the mod fetches, so a texture is used only when its base64 JSON decodes and every URL in it is on `textures.minecraft.net/texture/` with a hex name. Hints come from players, and a texture the check rejects falls through to the next source: the item's own, then the hint for its skin id, then the hint for the item, then the catalog's. Material `player_head` decides a skull, not `skull`, which is false for items such as the Sorrow Helmet that still carry a skin id.

## The viewer leaves the server its own `/pv`

dragsim.co has `/pv <vault>`, and players type it by habit, so the viewer's `/pv` must never swallow it. Fabric runs a client command only when the line parses, and sends the line to the server as typed when the parse fails with an unknown command. So the name argument refuses a number of one or two digits with that very error, and off dragsim.co, or with the setting off, the `pv` node's requirement hides it.

A player's name in chat links to their profile through `/dsmod pv`, not `/pv`. `/dsmod` is always the mod's, so a click with Profile Viewer off says so here instead of reaching the server as its vault command.

## The viewer's items look like a menu's to other mods

The viewer's items carry the server's `custom_data`, rebuilt by the backend in the shape a real item has, and its tooltip frame for their rarity. Other mods read a tooltip's item through Fabric's tooltip hook and its `custom_data`, so an item without them would show a bare tooltip where a menu shows theirs.

## The player model is not in the world

The player on the screen is a `RemotePlayer` that is not in the world. The game finds a player's skin through the tab list, which a player who is not online is missing from, so the model asks the skin cache by UUID as a head does. The renderer reads the entity's id, which the world assigns only to entities it holds, so the model takes a negative id no server entity has.
