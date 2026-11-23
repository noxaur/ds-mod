# Controls

What a player expects of each kind of control, from the desktop and from the mods they already use. A control ships with every line of its kind that applies. When the kind is not here, add its section in the same change.

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
- A key button on a screen records after a press, or Enter or Space, as SkyOcean's does: each key and each middle or side mouse button adds itself. A setting that takes one key is done at the first, and a right press or Delete clears it. Keys held together are done once all are let go, and keys in order once Escape is pressed. Escape, a left or right press elsewhere, or focus leaving ends it, and with nothing recorded the keys stay as they were. A cross beside it clears them. It shows `> keys <` while it records, as vanilla's controls do. `KitKeysButton` has all of this.
- Keys held together are named with ` + `, and keys in order with ` » `, as SkyOcean names them.
- A key that also does something else is ringed, as vanilla reddens a clash: `bad` when it does another of the mod's actions, `warning` when the game uses it. A line on the screen names the other use.
- Hold ends at the release, also when a screen has opened since. A new world or the feature's switch ends it too.
- Releasing puts back what the press changed.

## Favorite

A star that keeps a thing in a list of favorites, such as a setting in the settings' Favorites.

- A star sits on every thing that can be starred, beside its name, where it covers nothing at any length. A left press stars or unstars it, and the press does nothing else to the thing.
- A key stars the thing under the mouse, F by default as in NotEnoughUpdates, and the player can bind another key or a mouse button. It is left alone while a text field has focus and with Ctrl or Alt held, and its character never reaches a search.
- An unstarred star is faint and lights up under the mouse, and a starred one is in `action`. A tooltip names both ways: "Click or press F to star.".
- Favorites is the first entry of the list it belongs to, and holds the starred things in the order the full list has them, not the order they were starred. A short line at its top says how to add to it.
- A search looks through everything, so Favorites hides while it runs and each match shows once.
- A thing unstarred inside Favorites keeps its row, with its star empty, until the player leaves Favorites, so the rows below do not move under a second press and a slip is undone in place.
- Nothing starts starred, because the player picks their favorites. An empty Favorites says what will show there, and the first open of the screen goes to the first full category instead.
- The stars are saved with the settings.

## Cycle button

A button that steps through a few values, such as where a bind works.

- A press or Enter goes to the next value, and a right press, Shift with either, or the wheel goes back.
- It shows the value, and a caption above it names the setting.

## Settings row

One setting on a settings screen, such as ds-mod's own: the name with its description under it on the left, and the control of its kind on the right. MoulConfig's editor, which SkyHanni uses, and Resourceful Config's, which SkyOcean uses, are the peers. `SettingRows` has all of this, and `settings.md` says how to add a row.

- A dot before the name marks a value that differs from its default. A press on it resets the value, and its tooltip names the default, as Resourceful Config's reset button does. Backspace resets the row the keyboard is on.
- A row under a switch that is off dims, and stays editable, so a feature can be set up before it is turned on.
- A row whose settings are closed sums them up in muted text, such as `L, K` before Slot Lock's switch.
- The description shows two lines under the name. A name or a description cut short shows whole in a tooltip after a short hover.
- Tab moves the keys between the sidebar and the rows, and a press on a row gives the rows the keys. Up and Down move between rows, Enter and Space press the control, and Left and Right step a value, or open and close a row with settings. Focus looks like hover, and Escape gives the keys back before it closes the screen.
- A search matches a choice's values too, as MoulConfig's does, and opens the rows that hold a hit, as MoulConfig's accordions open. Those rows close again when the query changes.
- A search paints each word of the query wherever it occurs, ignoring case: in names, descriptions and headings in the match role, and in a fold's name, a dropdown's value and its menu's items in the match ink, so the player sees why each row was found.
- The narrator says the row under the mouse or the keys, as vanilla narrates its widgets: the name, the kind and the value, such as "F5 Views, choice, All, 1 of 3", then "changed from" the default and "dimmed" with the switch that is off, with the description as a hint and the row's place in the list. A change says the row again with its new value. An open menu says its lit item, such as "Skip Back, 3 of 3", and the sidebar and the tab bar say the line or tab under the mouse or the keys.
- A star after the name keeps the row in Favorites, the first section, as NotEnoughUpdates' favorites do. It shows on a starred row, and faint on the row under the mouse or the keys, lighting under the mouse. A press on it, or the Favorite Key (F) over the row or on the keys' row, toggles it, and its tooltip names both. The key types into a focused search instead, and does nothing with Ctrl or Alt held. Favorites lists the rows in the tree's order under their headings, and a row unstarred there stays until the player leaves, so a second press stars it again. The narrator adds "starred".
- A change applies at once, and the screen writes it to disk when it closes.

## Switch

A sunk 22x12 track with a raised knob, which slides right and turns the good role when on.

- A press anywhere on its row toggles it, and so do Enter and Space.
- A switch with settings has a 14x14 button after it that opens them under the switch, indented behind a guide line. Right opens them and Left closes them.
- A group whose rows are one feature has its switch beside the heading, and the heading reads muted while it is off.

## Number

A short sunk track with a raised accent fill, and a field beside it, as MoulConfig's slider has.

- The fill runs from the minimum, or from zero for a range through zero, with a tick at zero.
- A press on the track jumps there and a drag follows, past the track's ends too. The cursor shows a sideways resize over the track.
- The wheel over the track or the field steps it, and with Shift it steps ten, as do Left and Right.
- A press on the field opens it for typing with its value selected, and a digit typed on the row opens it holding that digit. Enter takes the number, clamped and kept to the steps, and the text turns `bad` while it is not a number, as Resourceful Config's does. Escape leaves it as it was, and a press elsewhere takes what was typed.

## Dropdown

A choice of one from a few named values: a raised button with the value and a down arrow, as MoulConfig's and Resourceful Config's dropdowns are.

- A press, Enter or Space opens a menu under it, lined up with its right edge, or over it where the screen has no room below. The current value has a check, and the menu opens lit on it.
- Up and Down light a value, Enter or a press takes it, and Escape or a press outside closes the menu, which still does its own job.
- Typing a letter lights the next value that starts with it, and the same letter again goes on to the next, round to the first, as a desktop list box does. Letters typed within a second of each other match the start together. The typing stays in the menu and starts no search. Space picks, so a prefix stops at a space. The mouse lights an item only when it moves, so it does not take back what the keys lit. Neither vanilla, which has no dropdown, nor MoulConfig's dropdown takes keys, so the desktop is the peer.
- A right press steps to the next value without opening, and Shift with it steps back. The wheel over the button steps both ways, and Left and Right step with the keyboard.
- A scroll of the pane closes the menu.

## Pick

A dropdown whose values are told apart by how they look, such as a rarity style or a theme. The button shows a preview of the value before its name.

- Its menu is a row of tiles, each a preview over its name, wrapping where the screen is narrow. The current one is ringed in the accent, and the one under the mouse or the keys is lit.
- The arrow keys move between tiles, and Enter or a press takes one. The rest is a dropdown's.

## Color picker

A color the player picks freely, such as a beam's, on a dropdown whose preview is a swatch of the color, with its hex and its opacity when it is see-through. Figma's picker is the peer, since no Minecraft mod has one worth copying. `ColorPick` in `core/ui` holds its rules, and `KitColorPicker` has all of this.

- A press, Enter or Space on the row opens a panel under it, or over it where the screen has no room below. `#` typed on the row opens it with the hex field holding `#`.
- The panel holds a field of saturation across and brightness down for the hue, a hue strip, an opacity strip on a checkerboard, a hex field, an opacity field in percent, and swatches: the last 8 colors picked, saved in the config so they outlast a restart, then the 16 chat colors. The current swatch is ringed in `accent`.
- A press on the field or a strip jumps there and a drag follows, past their ends too. The cursor is a crosshair over the field and a sideways resize over a strip. Escape during a drag puts the color back.
- The hue stays where it was while the color is gray or black, and the saturation while it is black, so dragging brightness back up finds the color again.
- The wheel over the field steps brightness, and over the hue strip, the opacity strip or the opacity field steps them, by ten with Shift.
- A press on a field selects its text. Hex takes 1, 2, 3, 6 or 8 digits, with or without `#`, and 8 sets the opacity too. Enter or a press elsewhere takes it, Escape leaves the color as it was, and the text turns `bad` while it is not a color.
- Tab and Shift+Tab move between the parts, and the part the keys are on is ringed as hover is. Arrows nudge the field by a percent, the hue by a degree and the opacity by a percent, ten with Shift. On the swatches the arrows move the ring and Enter takes one.
- A press on a swatch takes it. Every change applies at once, as in the settings.
- Escape, Enter on the field or a strip, or a press outside closes the panel, and the press outside still does its own job. The narrator says the part and its value, such as "Hue 32 degrees".

## Fold

More rows the player rarely needs, under a raised bar with an arrow, as MoulConfig's accordion is.

- A press on the bar, Enter or Space opens and closes it, and Right and Left do with the keyboard.
- Closed, the bar sums up what is under it in muted text, with each value joined by ` · `.
- Its changed marker shows when any row in it differs from its default, and a press on it resets them all.

## Ordered list

Which entries of a short list show, and in what order, such as the pieces of the equipment display. MoulConfig's draggable list, which SkyHanni uses for its text orders, is the peer: it lists the shown entries, a drag moves one, a plus adds a hidden one from a dropdown, a minus or a drag onto a bin removes one, a list that must keep one refuses to lose its last, and its search matches the entries. It takes no keys. ds-mod's lists every entry with a switch instead, so a hidden one is one press away and nothing sits behind a menu. `Order` in `core/config` holds its rules.

- Closed, the row sums up the shown entries in order in muted text, such as `Helmet, Chestplate, Pet`. A press on the row, Enter, Space, or Right opens it under the row, behind a guide line, and Left closes it, as a fold does.
- Each entry is a line of its own: a grip, the name, and a switch. A press on the switch or the name, Enter or Space shows or hides it, and it stays in its place, so showing it again puts it back where it was.
- A press and drag on the grip or the name moves the entry to the line under the mouse, and the others make room as it passes. Past either end it stays at that end. The cursor shows an up and down resize over the grip and during the drag. Escape during the drag puts the list back.
- Alt with Up or Down moves the entry the keys are on, and the keys follow it. Up and Down move between lines, and Left steps out to the row.
- The last shown entry cannot hide: its switch is drawn off-limits, and its tooltip says why.
- A search for an entry's name opens the list, so the hit shows.
- The changed marker and Backspace reset the whole list, and the narrator says each entry's name, whether it is on, and its place, such as "Boots, switch, on, 4 of 5".

## Delete

- A delete that cannot be undone asks for a second press on the same button, as SkyOcean's does. The first rings the button in `bad` and the screen's line says what the second will delete. A press anywhere else calls it off.

## List of things to edit

A list of things the player made, such as command binds, edited where it sits in the settings.

- Each thing is a line of cells that tell the things apart and say what each does, such as a switch, a name, the command, keys and where they work, so the player reads the list without opening anything. Fields a player always fills come first, and optional ones say so in their hint. The line under the mouse is ringed in `hoverOutline`.
- What does not fit on the line opens under it from ▸ at its end, one thing's details at a time, with the line's actions: up and down, Duplicate and Delete. A change takes effect at once, as in the settings.
- A choice of one from a few on a line, such as where it works, is a cycle button: a press steps on, a right press steps back. In the details, a choice from many, such as an area, is a cycle button too, not a grid of checkboxes.
- Actions on the whole list, Add, Add Group, Import and Export, sit under it. A new thing opens its first field to type into, and a new group its name, selected.
- An empty list says what to do and keeps its buttons. No example things ship.
- Groups are headings, with an arrow that folds the group away, a switch for the whole group and a count. The folds are kept, and a double press on a heading folds it too. ▸ on a heading opens its name, Export and Delete. A group with nothing in it shows a line that says what to drag there.
- A line is dragged by its grip, and a heading by its own, and the lines make room as it passes. A line dropped under another heading joins that group. Escape during the drag puts the list back.
- Up and down buttons move a thing a line, and are off where they would move nothing. A thing moved past a heading joins that group, and unfolds it.
- Up and Down from the row above enter the list. Inside, Up and Down pick a line, Left and Right fold and unfold a heading or move between a line's cells, Space switches the picked group, Enter opens a line's details or presses the cell, Alt with Up or Down moves the picked line or group, Delete deletes, Tab moves between text fields, and Escape leaves the list.
- A right press on a line opens its context menu, and so do the Menu key and Shift+F10.
- Keys are recorded on the keys cell with every convention of a key bind, and keys that clash with another thing or with the game are ringed, with the line under the list saying which.

## Context menu

- A right press opens it beside the mouse, kept on the screen, with what can be done to the thing under the mouse, which it picks.
- Items are verbs, such as Duplicate, Turn Off or Move Up, and the menu keeps the same length however many things there are, so no item per group. An item that would do nothing is left out. A delete in it asks for its second press as the Delete button does.
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

## Layout of the HUD

The HUD's blocks placed on the screen, as in SkyHanni's, Skytils' and Odin's editors. The HUD editor over the game, the chat screen and the settings' scaled copy of the screen all move blocks through `HudMover`, so they behave alike. The copy draws the real blocks, with the hotbar as a landmark.

- Every convention of a draggable thing. A dragged block lines up with the screen's edges and middle and with the other blocks within a few pixels, docks beside the hotbar near a spot it can dock in, and Shift places it freely. It never leaves the screen.
- The wheel over a block resizes it a step, and so do + and − with it picked. Shift with an arrow nudges ten pixels.
- A right press stacks the block again, undocked, and so do Backspace and Delete with it picked. The settings' changed marker stacks every block.
- A press picks a block, and the line under the copy names it with its scale beside −, + and Reset.
- In the copy, Tab picks the next block, and Escape leaves the copy, then closes the screen.
- With nothing on the HUD, the copy says so, since a block shows only where its feature has something to show.

## Places for tabs

A small menu with a place for a tab along its top and bottom edges, as the tabs show on every menu.

- A press on an empty place adds a tab there and opens its command to type. A press on a tab picks it, and its command, item and name sit beside the menu, or under it on a narrow pane.
- A tab is dragged to another place, and a tab already there takes its old place. Escape during the drag puts it back.
- The item is typed by its name and its icon shows beside the field, which reads red while no item has that name.
- With a tab picked, Left and Right move it along its edge, Up and Down to the other edge, Tab picks the next tab, Delete asks for a second press, and Escape leaves.
- The row's changed marker puts every tab back where it started.

## Navigation tree

A sidebar of sections, each with subsections it can show beneath it, beside a pane that scrolls, such as the settings screen. MoulConfig's editor, which SkyHanni and Firmament use, and Resourceful Config's, which SkyOcean uses, are the peers.

- A press on a section selects it, and the pane starts at its top. A press on a subsection, under any section, selects that section and jumps the pane to the subsection's heading, gliding there within the section already shown.
- A section shows its subsections while it is selected, or once its arrow opened it. Its arrow closes it, even the selected one, until it is selected again. What is open lasts while the screen does, across changes of section. One button beside Done collapses every section while any is open, and expands them all once none is.
- The selected section is marked twice, a lighter face and a bar in ink down its left edge. The subsection the pane is on is in full ink: the last heading near the pane's top, or the last once the pane is at its end. A jump marks its subsection even when the pane can't scroll its heading to the top.
- Up and Down move through the lines shown, Right opens a section and then steps into it, Left steps out and then closes it, and Enter opens what the keyboard is on. The line the keyboard is on is lit as hover is.
- The wheel, the scrollbar's thumb, and Page Up, Page Down, Home and End scroll the pane. The wheel over the sidebar scrolls it when it overflows.
- Typing anywhere searches, as MoulConfig does, and Ctrl+F goes to the search field, as Resourceful Config does. A query matches a row when every word is in its name, its description, or its group's heading, as MoulConfig matches. While it searches, the pane lists the hits under "Section › Subsection" headings, the sidebar hides arrows and subsections and shows each section's count, and a section with none is dimmed. A press on a section ends the search. With no hits the pane says so and offers Clear Search.
- Escape leaves the search field and keeps what was typed, and a second Escape closes the screen, as in MoulConfig.
- A name the sidebar cut short shows in full as a tooltip after a short hover, as in the game's own screens. The rows' own conventions are under Settings row.

## Tab bar

Browser tabs across the top of a pane, each one place of the screen with its own scroll and history, such as the settings screen's sections and searches. Firefox and Chrome are the peers, since no Minecraft mod has these.

- The bar is 18 tall on the sidebar's surface, with tabs sitting on its bottom line. A tab shows its place's icon and name, cut short with its whole name as a tooltip, and a × under the mouse and on the selected tab. The selected tab is in the pane's color and joins it. The last tab has no ×.
- Tabs share the room down to a narrowest width, and past that the strip scrolls sideways, by the wheel too, and keeps the selected tab in sight. The plus sits after the last tab, or after the strip once it scrolls.
- A press selects a tab. A press on its ×, a middle press on it, or Ctrl+W closes it, and closing the selected one selects the tab that took its place, or the one before it at the end. Ctrl+Shift+T reopens the last closed tab where it was, with its history.
- A drag sideways moves a tab, which follows the mouse and passes a neighbor once the mouse crosses the neighbor's middle. Escape during the drag puts it back.
- The plus or Ctrl+T opens a blank tab after the selected one, selects it and focuses the search. A blank tab's pane says what to do.
- A press in the sidebar takes the selected tab there, and Ctrl+press or a middle press opens it in a new tab after the selected one, in the background. Typing a search turns the selected tab into that search, as one step back.
- The mouse's back and forward buttons and Alt+Left and Alt+Right walk the selected tab's history. Ctrl+Tab and Ctrl+Shift+Tab go to the next and previous tab, Ctrl+1 to Ctrl+8 to that tab, and Ctrl+9 to the last. On macOS the key is Cmd. These work in the search field too.
- The tabs last until the game closes, as a browser's session does, so reopening the screen brings them back.

## Turntable

A model the player turns to look at, such as the player in the profile viewer. NEU's and Skyblocker's viewers only follow the mouse, so this is the mod's own; `Turntable` in `core/ui` holds its rules.

- It turns slowly on its own, so the viewer sees all of it without a press.
- A drag turns it: sideways around, and up and down within a limit, so it never turns over. On release it turns on from where it was let go.
- Escape during a drag puts it back where the drag began, and does not close the screen.
- With the mouse over it, the arrow keys turn it a step.
- The cursor shows that it can be grabbed.
- A settings Preview holds still instead, for the reason in [settings](../../../../docs/internals/settings.md#a-preview-is-a-row-that-holds-no-value), and starts turned a little so a tilt either way reads. Its arrows work while the keys are on its row, and only Left and Right, so Up and Down still move between rows.

## View buttons

A row of buttons that picks what a preview shows, such as Animations' Held, Swinging and Screen view. `HandPreview` in `core/hand` holds their rules.

- The picked view's button is pressed in, and the one under the mouse lifts.
- A press on a button shows its view.
- While the keys are on the row, Left and Right step to the view beside, and stop at the first and last. Up and Down still move between rows.
- The narrator says the view shown and that Left and Right change it.
- The pick lasts until the game closes, as the settings' tabs do.

## Scrolling list

- The wheel scrolls, the scrollbar's thumb drags, and Page Up, Page Down, Home and End jump.
- A list that grows keeps its place, unless it was at the end, where it follows.

## Own screen

- Done and Escape return to the screen it was opened over.
- A change shows at once.
