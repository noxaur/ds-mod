# Contributions

What players send the developers: recordings for a request, how items look, and lines the mod could not read. Publishing a request is in [operations/captures.md](../operations/captures.md#ask-players-for-a-capture).

## A contribution is a brief run that ends in an upload

An agent publishes a request, a brief with requirements, and the mod walks a player who qualifies through it. Its time limit runs from the moment the player accepts, because a run that waited for a key would sit on screen forever. A run that times out, is cancelled or disconnects is discarded, because an abandoned recording is the likeliest to be wrong, and an agent would read the part as the whole.

## The run's life is specified

A run crosses several threads and the disk, which outlives a quit or a crash, so a TLA+ spec models it. From it, an upload claims its file so a resend can't send it twice, only a whole redacted file is ever kept or sent, and the offers are fetched only after the start-up sweep, so the sweep never deletes a capture a run has begun.

## A failed upload is kept, and a refused one is deleted

A recording that fails to upload is kept, redacted, for the player to send again. One the backend refuses for good is deleted, since sending it again would fail again. Any other failure keeps it.

## The mod judges a request's requirements

The backend can't know what a player has seen, so the mod checks a request's requirements itself, from the places it has seen. These are the server's place names and not the mod's areas, because a request is most often for a place the mod has no feature in yet. A requirement the mod does not know is never offered, so an older mod can't offer what it can't judge.

## One switch covers everything the mod reports

Item sightings and unread lines go out under one switch, Report Game Data. A switch per kind was rejected, because it would ask the player again for each new kind, and a player who read the first prompt would have agreed to one kind only. Nothing is read while it is off, and a batch the backend refuses for good is dropped.

## An item sighting is the player's own inventory, agreed on by two players

dragsim.co's API sends no lore, enchantments or glint, so the backend learns how items look from players. Only the player's own inventory is read, because a menu can show another player's items with a name the redactor was never told. Any client can send anything, so the backend acts on a look only when two distinct players agree, caps the rows each player may hold, and takes textures only from Mojang's domain.

## An item's data leaves the machine through an allowlist

The server keeps copy ids, owners and more in an item's data, and may add a key at any time. So a sighting sends only the keys on an allowlist, and a head only its skin's address, because the head's own property names the player who uploaded it. Dropping known private keys was rejected, because a key the server adds later would leave the machine unreviewed.
