# Profiles

How the profile viewer gets another player's profile and draws its items as the game would.

## The backend looks players up, so dragsim.co is asked once for many clients

dragsim.co's player API has no key and no stated limit, only an owner's goodwill. So only the backend calls it, and it keeps copies: an answer is served for two minutes and kept for a day, to answer when dragsim.co is down. A name it does not know is remembered for a minute, so a typo asked in a loop costs dragsim.co nothing. Letting the mod and the website call it directly was rejected, because every client would then ask on its own.

## A body that no longer decodes is dragsim.co's change

A body that no longer decodes is dragsim.co's change and not the client's mistake, so it is logged and treated as unavailable, and a stale copy covers it. Treating it as a defect was rejected, because it would answer an empty 500 and hide the stale copy a player could have had.

## Containers come from a snapshot, not a fresh lookup

A profile is answered without its containers, and each answer keeps a snapshot of them for ten minutes. Rebuilding every tooltip of thirty storage pages is wasted when a player opens one tab, and fetching a container fresh would show a tab from a later moment than the overview beside it. When the player hid their inventory, the items are dropped before anything is kept.

## The backend drops nothing dragsim.co sends

The viewer must be able to show every piece of data dragsim.co sends. So a field is added to the contract and never removed, and a key the backend does not map is logged by name, so a new section shows in the logs before a player misses it. Each section is read on its own, because a section that changes shape should cost only itself.

## The backend rebuilds item tooltips, because the server's API sends none

dragsim.co's player API sends an item's data and no lore, so the backend rebuilds the tooltip from the item and the server's catalog, and the mod draws the item as the game does. Tests compare the rebuild with tooltips recorded in the game. An item with odd data draws what it can and never throws.

## A glint is decided by what players saw

A glint is not in the data. The catalog's word wins, and otherwise players' sightings decide: an item they always saw glint glints.

## The viewer leaves the server its own /pv

dragsim.co has its own /pv for vaults, and players type it by habit, so the viewer must never swallow it. A number of one or two digits is refused as an unknown command, which sends the line to the server as typed, and the viewer's command is hidden off dragsim.co. A chat link opens a profile through the mod's own command, because that one is always the mod's.

## The viewer's items look like a menu's to other mods

The viewer's items carry the server's item data, rebuilt in the shape a real item has, and its tooltip frame for their rarity. Other mods read a tooltip's item data, so an item without it would show a bare tooltip where a menu shows theirs.
