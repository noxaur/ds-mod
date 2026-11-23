# Player data

How the mod keeps what it knows of the player's own account, and why it keeps it for each dragsim.co profile.

## Every feature reads the player's data from one layer

What the mod knows of the player, such as the containers they opened and a tracker's totals, lives in one layer that the mod folds before any feature sees an event, and a feature reads it there. Before it, the item overview kept its own copies for the session only, so that a copy could not outlast a switch to another profile, and every tracker kept one total for all profiles. Each feature telling profiles apart for itself was rejected, because each would need the switch right, and one that got it wrong mixed two profiles' items.

## The profile is told by the Profile Management menu

dragsim.co names the profile only in its Profile Management menu, which marks the one in use, and a switch sends no chat line, no world change and no disconnect. So the layer takes the profile the player clicks there, and the switch happens when the server then closes the menu, unless the server refused it first. The menu's own mark corrects the layer whenever the player opens it. Asking the backend was rejected, because it can name the profile the player left for as long as it keeps an answer, and nothing while it is down. It names a profile only before the menu ever has.

## What the mod saw before it knew the profile goes to the first one it learns

Until the player opens the menu or the backend answers, the layer keeps what it sees under no name, and the first name it learns takes it. A player who never opens the menu then still has every feature working on one profile. Holding back until the profile was known was rejected, because a new player would see nothing until they found the menu.

## A total saved before profiles goes to the profile the player is on first

A tracker's file from before the layer held every profile's total. The first profile the player plays after updating takes it, and the old file is kept beside it. Splitting it between profiles was rejected, because nothing tells which profile earned which part.

## The player's data is one file per account, encoded off the client thread

Each Minecraft account has one file, so two accounts on one computer keep apart. A copy of every Storage page is too large to encode on the client thread without dropping a frame, so the writer encodes it on its own thread, from a state that never changes in place.
