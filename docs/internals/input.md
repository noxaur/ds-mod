# Input and the camera

Features that read the player's keys, mouse and clicks, or change how the player's view and hand are drawn.

## Free Look takes the mouse before the player does

The player's rotation is what the game aims with and what it sends to the server. Free Look must leave it alone, or every look around would turn the head other players see and move the crosshair's target. So `MouseTurnMixin` stops the turn before it reaches the player and `Cameras` spends it on an angle of its own, which `CameraMixin` gives only to the camera. Setting the rotation and putting it back afterwards would send the turn in between.

Camera's height and tilt are applied after the game has put the camera at the eyes and before it backs the camera out. The game's own wall check then runs from the lifted start along the tilted angle, so neither setting can push the view through a block.

The key's press comes from `KeyMapping.click`, the place the game counts presses for its own key binds, and its release is read from the key's state each frame. A press read from the state would miss a tap that starts and ends inside one frame, and Toggle mode is nothing but taps.

## Animations change the drawing and never the swing

The client sends the swing packet in `LocalPlayer.swing` before the game asks how long the swing lasts, and nothing that decides an attack reads the swing's progress, so Swing Speed changes only how the swing looks. It still slows or speeds the player's own arm in third person, because that arm reads the same progress. No Equip Reset holds the item up where the game would lower it, and the attack cooldown itself is left alone.

The item is moved as the hand's pose is pushed, before any branch of the renderer runs, because eating and drinking put the item in the hand twice and a later hook would move it twice. It is turned and scaled last, just before it is drawn, so it turns and grows about the grip whatever the swing or a use did to it. Every swing style is the resting pose when the swing is at 0, as the game's own swing is, so a hand that is not swinging is the game's own whatever the style.

`AnimationsTest` holds the defaults to the game's frame pixel for pixel. It turns off the vignette, which eases toward the light around the player for half a minute after a join and shades the whole frame, and the attack indicator, which a swing at the air starts.

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

Minecraft keeps an open container's position and size on screen in protected fields, and Fabric's screen events fire outside the transform that places the slots. `ContainerScreenAccessor` reads the four fields, and the slot under the mouse. `ChatComponentAccessor` does the same for the chat's messages and wrapped lines, which Copy Chat needs to tell what a press landed on. `ChatScreenAccessor`, `CommandSuggestionsAccessor` and `SuggestionsListAccessor` read where the command suggestions and a command's usage are drawn, which Drag In Chat leaves the mouse to.
