# Contributions

What players send the developers: recordings for a request, how items look, and lines the mod could not read. Publishing a request is in [operations/captures.md](../operations/captures.md#ask-players-for-a-capture).

## A contribution is a brief run that ends in an upload

A request is a brief plus requirements, which an agent publishes to the backend. The mod offers it to a player who qualifies, and the same `BriefRun` that `-Pbrief` uses walks them through it.

A request has a time limit measured from the moment the player accepts, not from their first keypress. A run that waited for a key would otherwise sit on the screen forever once the player walked away. A run that outlasts the limit, is cancelled, or loses its connection is discarded and not uploaded, because a recording the player abandoned halfway is the likeliest to be wrong. A capture whose writer lost a record or did not finish is discarded the same way, since an agent would read the part as the whole. A change of world is not a disconnect, because a brief's warps change the world on one connection.

The run's life crosses the client thread, the io thread, the backend's thread and the disk, which outlives a quit or a crash, so [`specs/Contribute.tla`](../../specs/Contribute.tla) models it. Three decisions come from it:

- An upload claims its file, and a resend skips claimed files. A resend that listed a file an upload was still sending would send it twice and fail on the deleted file. A file the backend has already kept stays claimed when deleting it fails, so a resend in the same game does not upload it again. A claim lives in memory, so after a crash between the backend keeping a file and its delete, the next start sends it once more, and the backend refuses it because it keeps one run per player.
- The redactor writes a `.part` and renames it, so only a whole, redacted `.gz` is ever kept or sent. The next start deletes anything else in `dsmod/contributions/`, which is a raw capture or a half redaction.
- The offers are fetched only after that sweep, so the sweep can never delete a capture a run has started.

A recording that fails to upload is kept as a redacted `.gz` named for its request, and `/dsmod submit` sends what is there. One the submissions route refuses for good (404, 409 or 413) is deleted, since sending it again would fail again. Any other failure, an empty 400 or a failed sign-in included, keeps it. Quitting waits a few seconds for an upload in progress, and one that does not finish stays for the next `/dsmod submit`.

## The mod judges a request's requirements

The backend cannot know what a player has seen, so the mod checks a request's requirements itself. Today the only kind is an area, met when the tab list's `Area` row or the sidebar's `⏣` place has ever named it, so the places seen are saved. These are the server's names and not an `Area`, because a request is most often for a place the mod has no feature in yet. A kind the mod does not know is never offered, so an older mod cannot offer what it cannot judge.

Skipping tells the backend, which hides the request from that player and nobody else.

## One switch covers everything the mod reports

Item sightings and the report of unread lines go out under Report Game Data. A switch per kind would ask the player again for each new kind, and a player who read the first prompt would have agreed to one kind only. One general switch is asked once, and the player docs say what goes today. Both share one batch and backoff (`Batch`), and nothing is read while the switch is off. A batch the backend refuses for good (400, 404, 409, 413, 422) is dropped, because sending it again can only fail again, and any other failure is tried at the next report.

Unread lines are redacted like a capture, and the backend keeps only one row per kind of line.

## An item sighting is what the player's own inventory shows, agreed on by two players

dragsim.co's API sends an item's data but no lore, enchantments or glint, and only some skull textures, so the backend learns how items really look from players.

- **Only the player's own inventory, never a menu's slots.** A menu can show another player's items, such as an auction's seller or a trade, and the redactor cannot find a name it was never told. The inventory is read from the tick, and a slot is looked at again only when its stack changes. A look the session has seen costs a few component reads, because the item is built only for a new one.
- **Two players must agree.** Any signed-in client can send anything, and one client sending a thousand times is still one, so the backend counts distinct players and acts on a look only when at least two agree.
- **Caps per player and per table.** A player may hold at most 2,000 item rows, so one account cannot fill the table before honest players arrive. The 20,000-row table cap is only a backstop against many accounts. The caps are soft against one account sending many reports at the same instant: D1 cannot hold a transaction across the count and the insert, so a burst can pass the cap once before it is refused, and the rows it adds cannot sway a hint, which still needs a second player.
- **Textures only from Mojang.** A texture is refused unless every URL is on `textures.minecraft.net`, so a client cannot make the backend or an agent's browser fetch what it chose.
- **`custom_data` through an allowlist.** The allowlist reaches inside `item_data` and `extra` too. The server keeps copy ids, owners and more there, and a key it adds later would otherwise leave the machine unreviewed.
- **A head as its skin's address alone.** It is rebuilt from the item's `skin`, because the server's `textures` property names its player beside the skin. The capture redactor drops skins, so a sighting takes it from the item before redaction.
