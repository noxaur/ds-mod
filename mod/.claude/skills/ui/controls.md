# Controls

What a player expects of each kind of control, from the desktop and from the mods they already use. A control ships with every line of its kind that applies. When the kind is not here, survey it first, then add its section in the same change.

Peers to survey: SkyHanni, SkyOcean, Chat Patches, Chatting and Firmament. Their source shows which conventions Minecraft players already know, and a subagent can read it for you.

## List selection

Items a player picks from, such as chat messages or slots.

- A press selects one item and sets the anchor. A press and drag selects the range from the anchor, and a drag past the edge scrolls.
- Shift+press extends from the anchor to the pressed item.
- Ctrl+press adds or removes one item, and Ctrl+Shift+press adds a range. On macOS the key is Cmd.
- A press on an item inside a multi-selection keeps the selection and reopens its menu.
- Ctrl+A selects all, Ctrl+C copies in the default form, and Shift+Up and Shift+Down move the end of the selection.
- Escape or a press elsewhere clears the selection.
- The selected items are drawn, and the menu says how many there are.

## Context menu

- It opens beside the mouse, kept inside the screen, with the action the player most often wants nearest. It covers neither the thing that was pressed nor its neighbors, so the next press can correct a miss.
- Escape or a press outside closes it, and the press outside still does its own job. One menu is open at a time.
- Hover marks the entry the next press takes, and the arrow keys and Enter work.
- A choice closes the menu and shows what it did.

## Key bind

A key that works in the world, such as Free Look's.

- Its setting sits beside the feature's switch, takes a key or a mouse button, and can be cleared to none.
- A feature the player holds a key for offers Hold and Toggle.
- A press comes from the game's key event, as `Cameras.pressed` takes it, so a tap shorter than a frame counts and a key typed into a screen does not. A held key repeats its event, and a repeat is not a press.
- A key that works on a screen comes from that screen's key and mouse events, as `SlotGuards` takes it. It is left alone while a text field has focus, and it acts on the thing under the mouse.
- A press and release in one place is the plain action, and a press held to another place joins the two. A line follows the mouse while it is held. Afterwards the join shows only under the mouse, over what it crosses, so it clutters nothing at rest.
- A key that marks one thing and joins nothing marks each thing the mouse sweeps over while it is held, the way the first one went, as a paint stroke does. Things it cannot mark are passed over, and only the first one says why.
- A key button on a screen records after a press, or Enter or Space, as SkyOcean's does: each key and each middle or side mouse button adds itself. Keys held together are done once all are let go, and keys in order once Escape is pressed. Escape, a left or right press elsewhere, or focus leaving ends it, and with nothing recorded the keys stay as they were. A cross beside it clears them. It shows `> keys <` while it records, as vanilla's controls do. `KitKeysButton` has all of this.
- Keys held together are named with ` + `, and keys in order with ` » `, as SkyOcean names them.
- A key that also does something else is ringed, as vanilla reddens a clash: `bad` when it does another of the mod's actions, `warning` when the game uses it. A line on the screen names the other use.
- Hold ends at the release, also when a screen has opened since. A new world or the feature's switch ends it too.
- Releasing puts back what the press changed.

## Cycle button

A button that steps through a few values, such as where a bind works.

- A press or Enter goes to the next value, and a right press, Shift with either, or the wheel goes back.
- It shows the value, and a caption above it names the setting.

## Delete

- A delete that cannot be undone asks for a second press on the same button, as SkyOcean's does. The first rings the button in `bad` and the screen's line says what the second will delete. A press anywhere else calls it off.

## List of things to edit

A list of things the player made, such as command binds.

- The list sits in a sunk container on the left, with its scrollbar inside, and everything about the picked thing is on the right, always shown, so nothing hides behind a More button or a second window. A change takes effect at once, as in the settings.
- A choice of one from a few, such as an area, is a cycle button, not a grid of checkboxes.
- Actions on the whole list, such as Import and Export, sit in the screen's top bar.
- Opening the screen picks the first thing, so its details are there without a click.
- A row shows what tells the things apart, such as a switch, a name and keys. Fields a player always fills come first, and optional ones say so.
- A row shows what tells the things apart and what each does, such as a switch, a name, the command, keys and where they work, so the player reads the list without picking each row. The row under the mouse is ringed in `hoverOutline`.
- Groups are headings in the list, with an arrow that folds the group away, a switch for the whole group and a count. The folds are kept. Picking a heading shows the group. A row or heading is dragged to move it, and a row under another heading joins that group. A group with nothing in it shows a line that says what to drag there.
- With nothing on the right focused, the keyboard works the list: Up and Down pick, Left and Right fold and unfold, Alt with Up or Down moves the picked row or group, and Delete deletes.
- A right press on a row opens its context menu.

## Context menu

- A right press opens it beside the mouse, kept on the screen, with what can be done to the thing under the mouse, which it picks.
- Items are verbs, such as Duplicate, Turn Off or Move to Dungeons. A delete in it asks for its second press as the Delete button does.
- Up and Down light an item and Enter runs it. Escape or a press outside closes it, and that press still does its own job.

## Range slider

- A click on an empty stretch adds a section, and a press and drag draws one.
- A drag on an end resizes a section, and a drag on the middle slides it.
- Right-click removes a section, and Delete removes the one last touched.
- Sections that touch become one, and come apart again if the drag backs off before release.
- The cursor shows what a press will do. With focus, the arrow keys slide the section.

## Text field

- Ctrl+A, Ctrl+C, Ctrl+X and Ctrl+V work, and so do Home, End and Ctrl+Left or Right by the word.
- Double-click selects a word. Escape leaves the field and Enter commits it.
- Vanilla's `EditBox` has these, so build on it.

## Draggable thing

- A drag moves it from where it was grabbed, and the thing stays on the screen.
- Arrow keys nudge it one pixel. Escape during a drag puts it back.
- The cursor shows that it can be grabbed.

## Scrolling list

- The wheel scrolls, the scrollbar's thumb drags, and Page Up, Page Down, Home and End jump.
- A list that grows keeps its place, unless it was at the end, where it follows.

## Own screen

- Done and Escape return to the screen it was opened over.
- A change shows at once.
