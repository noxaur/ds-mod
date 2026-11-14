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
