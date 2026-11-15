# Surfaces

Every type of interface ds-mod can show, grouped by surface. A feature uses the smallest type that carries its information.

## Over gameplay

**Chat**
- Mod message: a prefixed line the mod prints.
- Notice: a feature's name, a separator and a short detail, for an update that needs no action.
- Interactive message: click to run a command, copy or open a link, and hover for detail.
- Updating message: one line edited in place, such as a progress count.
- Filter: hiding a server line.
- Rewrite: recoloring, shortening or stacking repeated server lines.
- Selection: picking lines of the open chat to copy or act on, with a menu for the choice.

**HUD**
- Text line: one value, such as a timer or a stat.
- List or table: rows with optional item icons, as in a tracker.
- Bar: progress, cooldown or health.
- Icon: an item or texture, with an optional count.
- Graph: a small rate-over-time line.
- Compass or minimap: direction and nearby points.
- Crosshair indicator: a small mark near the crosshair, such as a cooldown ring.
- Hotbar overlay: cooldowns or marks on hotbar slots.
- Off-screen pointer: an arrow at the screen edge pointing to a world target.

**Alert**
- Title: large center text.
- Action bar: the small line above the hotbar.
- Sound: a cue with no visual.
- Screen effect: a flash, tint or vignette.
- Out-of-game: an OS notification or taskbar flash when the player is tabbed out.

## Inside a screen

**Container overlay**
- Slot highlight: a colored background or border.
- Slot text: a label in the corner, or a replaced stack count.
- Slot dimming: graying out or hiding what doesn't matter.
- Slot link: lines or numbers that connect slots or show an order.
- Side panel: a HUD-style block beside the container.
- Added controls: buttons or a search field on the container.
- Title edit: changing the container's title.

**Tooltip**
- Added lines: price, stats or source.
- Edited lines: hidden, reordered or reformatted server lines.
- Rich content: icons, bars or a comparison inside the tooltip.
- Held-key detail: more lines while Shift is held.

**Own screens**
- Settings: the MoulConfig screen.
- HUD editor: drag to position and scale.
- Feature screen: a full screen the mod owns, such as tracker history.
- Vanilla screen addition: a button on the pause menu or inventory.
- Vanilla screen restyle: a game menu drawn by the kit in the theme, and moved where it should sit, as Themed Menus and Stable Menus do.

## In the world

**Markers**
- Waypoint: a beam with a label and distance.
- Floating text: a label anchored to a position or an entity.
- Block highlight: an outline or filled box on a block.
- Entity highlight: a glow outline, hitbox or tint.
- Ground marker: a flat shape drawn on a surface.

**Lines and trails**
- Tracer: a line from the crosshair to a target.
- Connector: a line between two world points.
- History trail: where the player or an entity has been, fading with age.
- Route: a path ahead through ordered points, with the next one marked.
- Trajectory: the predicted arc of a projectile.

**Areas**
- Radius: a circle, sphere or cylinder around a point or entity.
- Region: the border of a named zone.
- Grid: chunk or plot borders.

**Entity and block edits**
- Name tag edit: added, shortened or recolored text above an entity.
- Entity bar: health or a timer above a mob.
- Damage number: a reformatted or merged hit number.
- Hiding: removing entities, particles or armor from view.
- Particle effect: particles the mod spawns to mark something.

## The server's HUD

- Sidebar: hidden, reordered or reformatted lines.
- Tab list: compacted or regrouped.
- Boss bar: hidden or restyled.
- Server action bar and titles: suppressed, or moved into a HUD element.
- Vanilla HUD: hiding or replacing hearts, hunger and the XP bar.

## Sound and input

- Sound: a sound the mod plays, or a server sound it mutes or replaces.
- Command: a `/ds…` command the player types.
- Keybind: a key the player presses.
- Screen input: a press, drag or key the mod handles on a vanilla screen. Its conventions are in [`controls.md`](controls.md).
- Tab completion: suggestions while the player types.
