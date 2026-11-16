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

A bind of keys held together needs every press and release, wherever they happen, or a Shift held before a menu opened would be missed. So `KeyInputMixin` and `MouseInputMixin` hand each one to `CommandKeys` before the game acts, and `BindMatcher` in `core` holds the keys that are down. Whether a press may fire is a separate question, asked at the press and again on the tick that would finish a sequence still waiting out its gap: in the world, or in a container menu with no text field focused, and never with F3 held. A waiting sequence is dropped when Command Binds is off, or when no bind still live covers both where the keys were typed and where the player is now. A bind set to both still covers a move between the world and a menu. One set only to the world, or only to menus, does not, because the gap would otherwise send it after the player had left that place. `KeyMapping.click` would not do, because the game calls it only with no screen open.

GLFW repeats a held key as its own action, and a repeat is not a press. A release can go unseen, as when the window loses focus, so each tick forgets keys that are no longer down.

A sequence fires as soon as no longer sequence could still follow, so a bind of F7 then F8 needs no wait unless another bind starts with F7, F8. Otherwise it waits for the gap from the settings, as SkyOcean does.

The settings ring a key that the game uses too. The game's debug keys work only with F3 held, so F3 is the only one of them that counts, and F6, which the game binds to F3+F6, is free.

## A locked slot refuses the click before the game predicts it

The client moves the item in its own inventory as it sends a click, so a click dropped at the packet would leave the player looking at a move the server never made. Every pick up, Shift click, number key, throw and double click in a menu passes through `AbstractContainerScreen.slotClicked` first, so `SlotClickMixin` refuses there and nothing is predicted or sent. The drop key outside a menu never reaches a screen, which is why `DropKeyMixin` exists beside it.

A lock is an index in the player's own inventory. A menu numbers its slots its own way, and the same inventory slot has another number in a chest than in the inventory screen, so a lock kept by menu index would move to another slot with each menu.

An item lock is the ID the server writes in `item_data.uuid`. In the captures every item that does not stack has one and no stackable item does, so two swords of one kind are told apart and a stack cannot be locked this way. Reading it copies the item's whole data tag, so `SlotGuards` keeps each ID by the data component, which stacks share.

A Shift click lands wherever the menu's own code finds room, and each menu orders that its own way. When that room includes a locked slot, `SlotGuards` still fills every unlocked slot the menu would use, a partial stack included, and repeats a crafting or furnace result until the source is empty or those slots are full. It refuses the click only when every slot that could take any of the stack is locked. The clicks are the ones a player would send: a pick up, a place, and a right click for each item a partial slot still has room for. A stack already on the cursor is set down outside that move and picked back up, because a Shift click never uses it. That needs the menu's rough order and never its exact one.

The swap-hands key is not guarded. On dragsim.co it opens the server's menu and swaps nothing.

## Every place the mod acts for the player is approved in the conversation that changed it

Whether the mod may send input for the player, or refuse the player's own, is the user's decision. A lint over the source caught only the method names its author thought of, and an exemption comment let whoever wrote the line approve it. So `InputSitesTest` reads the compiled release classes, where an alias, a wrapper, a lambda or a call split over lines is the same instruction, and fails on any site that `mod/input-sites.txt` does not list as it now is.

A gate is audited as a whole class, because when it acts matters as much as the line that acts. Its hash leaves out line numbers and local names, so moving code keeps the audit and changing it does not. A call into a gate that acts, or a read of one of its fields, from outside the gates is listed without a hash, so a new trigger needs an audit and an edit to an old one does not. It counts whichever of the gate's methods it calls, because tracing which ones send missed callbacks kept in a field or built by reference. A gate that only refuses a click or adds a chat link needs no list of callers, because the player still makes the move. Every mixin is a gate, since a mixin can rewrite Minecraft's code without calling anything the scan knows, except one of nothing but accessors that return a value, which can only read.

A mixin that only hooks drawing is approved by its hooks, not its hash, because drawing mixins are the gates edited most and an edit inside one cannot reach input. It counts as drawing only when every hook is in a method named for drawing (`extract`, `submit`, `render`, `draw`), at no call the scan counts as input, on a target that is not the player, and every other method it adds is a `dsmod$` helper, because an unmarked method of a target's name replaces it. Anything else is hashed, so a hook the rule does not know fails closed. Its calls into a gate that acts are listed, as any class's outside the gates are.

Most changes to a site are refactors that send and refuse exactly what they did, and having the user edit the file for each one cost their time without catching anything. So the test writes the whole file from the scan and fails once, like a feature's transcript, and its message draws the line: the agent keeps a site that sends and refuses as before, and asks the user who prompted it, in the conversation, before it keeps one that sends, refuses or triggers anything new. The user's yes is stamped on the site with their GitHub login and the time, by `-Papprove` and `-PapprovedBy` on the test run, and the stamp goes when the site changes, so a stamp always means the user saw that exact site. Each gate's line names the features it asks and is followed by what it does, so the diff in the pull request reads as a change of effect, and the message says when a changed gate does the same as before. Parallel pull requests touch the same lines, so git merges the file as a union, and the test writes it again from the merged code.

## Private fields are read through accessor mixins

Minecraft keeps an open container's position and size on screen in protected fields, and Fabric's screen events fire outside the transform that places the slots. `ContainerScreenAccessor` reads the four fields, and the slot under the mouse. `ChatComponentAccessor` does the same for the chat's messages and wrapped lines, which Copy Chat needs to tell what a press landed on. `ChatScreenAccessor`, `CommandSuggestionsAccessor` and `SuggestionsListAccessor` read where the command suggestions and a command's usage are drawn, which Chat HUD Editor leaves the mouse to.
