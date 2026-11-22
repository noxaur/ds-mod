# Input and the camera

Features that read the player's keys, mouse and clicks, or change how the player's view and hand are drawn.

## Free Look takes the mouse before the player does

The player's rotation is what the game aims with and sends to the server, so Free Look must leave it alone, or every look around would turn the head other players see. So the turn is stopped before it reaches the player and spent on an angle only the camera uses. Setting the rotation and putting it back afterwards was rejected, because the turn would be sent in between.

## Camera settings apply before the game's wall check

Camera's height and tilt are applied before the game backs the camera out from the eyes. The game's own wall check then runs from the lifted start along the tilted angle, so neither setting can push the view through a block.

## Animations change the drawing and never the swing or the right click

The swing packet is sent before the game asks how long a swing lasts, and nothing that decides an attack reads the swing's progress, so Swing Speed changes only how the swing looks. The attack cooldown itself is left alone. 1.7 Blocking reads whether right click is held and only draws the pose, because right click casts a sword's ability on the server, and the click must reach it as the game sends it.

## Player Model shapes the drawing, never the entity

The game's own scale would resize the model, but it also reaches the hitbox, the camera's height and the server, and Player Model must change none of them. So its size, tilt, spin and pose apply only while the model is drawn. The pose applies to every humanoid model, because each piece of armor is posed on its own, and a pose set only on the player would leave the armor's arms behind.

## A command bind sees every key, and fires only in the world or a menu

A bind of keys held together needs every press and release, wherever they happen, or a key held before a menu opened would be missed. So every key reaches the binds before the game acts on it. A bind fires only in the world, or in a menu with no text field focused, and never with F3 held. The game's own key-press hook was rejected, because it runs only with no screen open.

## A locked slot refuses the click before the game predicts it

The client moves the item in its own inventory as it sends a click, so a click dropped at the packet would leave the player looking at a move the server never made. So a locked slot refuses the click before the game predicts it. A lock is kept by the player's own inventory slot, because each menu numbers slots its own way, and a lock kept by menu slot would move with each menu.

## An item lock is the server's item id

An item lock is the id the server gives every item that does not stack, so two swords of one kind are told apart.

## Every place the mod acts for the player is approved by the user

Whether the mod may send input for the player, or refuse the player's own, is the user's decision. So a test reads the compiled release classes and fails on any place that acts that the approved list does not hold as it is now. A lint over the source was rejected, because it caught only the names its author thought of, and an exemption comment let whoever wrote the line approve it. The agent asks the user before it keeps a place that does anything new.

## Forge Craft sends one click per key press

Forge Craft repeats a forge craft from the keyboard, and each press sends one click, the next of the four the player made by hand, so the player still makes every step and sees each menu. One press that sent every click was rejected, because that is automation. A press waits up to a second for the menu it clicked to give way to the next, so a quick second press cannot land on the wrong button, and a menu the server never replaced takes a click again after that.

## Wardrobe Keys holds a press until the server's second is up

dragsim.co refuses a wardrobe click for a second after the menu opens, so a key pressed in that second is held and clicked when it ends, as the user asked. Dropping the press was rejected, because the player would have to press again. Each press is still one click on the button its key names, and only the last held press is clicked. The second starts again when any menu opens, because a new menu is a new opening to the server.

## Auto Left Click and Auto Right Click press the game's own key once a tick

dragsim.co allows auto clicking while the player walks, the user confirmed. Each click is the press the mouse makes on the attack or use key, so the game gives it every check a real click gets. The user asked that a button never pass 9.9 a second, so while a feature is on, the player's own clicks share its budget and one past it is dropped. Clicks go out on ticks but the budget refills by the clock, since after a freeze the game runs the missed ticks at once. Calling attack or use directly was rejected, since it skips those checks.

## Ctrl Click undoes the game's right click on macOS at the press alone

On macOS the game turns a Ctrl left press into a right press, and counts it so the release that follows turns too. Ctrl Click undoes only the press and leaves every release to the game. Undoing the release too was rejected, because a setting flipped while the button is down would leave the count up and turn a later plain release into a right one.
