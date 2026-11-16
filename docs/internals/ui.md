# UI

How the mod draws what features hand it. How to pick a surface and build on it is in the `ui` skill, [`mod/.claude/skills/ui/SKILL.md`](../../mod/.claude/skills/ui/SKILL.md).

## A feature states a role, the text layer owns the color

Minecraft mods usually write color codes inline, and their colors drift from feature to feature. In ds-mod a feature says what a piece of text means, such as body, value or muted, and `core.text` maps that role to a color. The palette then lives in one file, and `text-by-role` in `Rules.kt` fails the build on a color code or a raw color anywhere else in the release code.

Text the server sent is a separate type, `ServerText`, which carries whatever color the server chose. A feature reads one and builds the other, so the server's colors cannot leak into what the mod shows.

## UI layers keep what they are given

Each surface (chat, HUD, alerts, container slots, tooltips, world marks) has one object in `edge` that draws a plain model from `core`. A model is handed over when something changes, and the layer converts it to Minecraft's types once and redraws it from then on. A feature that built its text every frame would allocate in the render loop, where a dropped frame is a bug, so the layers offer no per-frame hook. The exception is a world box that follows the view, below.

## Chrome is drawn in code, from a theme

Panels, buttons, slots and bars are a few filled rectangles each, drawn by `Kit` in the colors of a `Theme`. Minecraft's own screens stretch nine-slice textures instead, and ds-mod does not: a second look would need a second set of textures drawn by hand, and a new shape would need an image editor. In code a theme is one list of colors and a shape is a function, and `draw-with-kit` in `Rules.kt` keeps every other file from drawing its own. Only the icons are textures, white so that one sprite takes any color.

The one exception is the Hotbar look of a HUD block, which cuts its slots from the game's own hotbar sprite. The point of it is to match the player's hotbar, and only the sprite follows their resource pack.

The slot is the one shape drawn dozens of times a frame, so `Kit` paints it into a texture from the theme's colors once per theme and blits it. Five fills per slot made a themed inventory cost 0.2 ms a frame. The paint runs at the end of a client tick: made while a frame was being built, the texture broke the drawing of every frame after it.

## Two themes, and role text only on dark ground

Two themes ship, because the references players know disagree: resource-pack screens are dark, and Minecraft's own are light gray. A light panel can't carry the text layer's colors, which were picked for chat and the HUD and wash out on gray. So a label on a panel is not role text. It is a plain string in the theme's ink, and role text sits only on a well or the HUD's plate, which stay dark in every theme. A theme that made those light would break every colored row.

## An item on the HUD is drawn from the mod's own record of it

dragsim.co's armor is a player head with a skin and leather with a dye, and its items have their own tooltip frames, so an `Item` keeps how the item looks as well as what features read: the skin, the dye, the glint override and the tooltip style. `Hud` rebuilds a stack from the `Item` alone.

An earlier version asked the poller for the very stack an item was read from, but the poller lets go of a stack once its menu closes or another pet comes nearer, and the icon then fell back to a Steve head and brown leather. The Pets menu, which closes before its pet is drawn, could not have been drawn that way at all.

The skin is kept as the last part of its `textures.minecraft.net` address, without the server's signature or the uploader's name the property holds. The game draws an unsigned skin on a head, as it does for a head a command gives, and the redactor drops the skin as it drops every other.

## The chat keeps what it draws over the HUD

Drag In Chat moves the live HUD on the chat screen, where Minecraft draws the chat's rows, its input, the list of command suggestions and a command's usage over the HUD. So a press or a turn of the wheel there is the chat's, even over a block, and a block is the mouse's only where the player sees it. The suggestions' bounds are private, so accessors read them. A press the chat would leave unused is not enough: a plain line takes no left press but scrolls with the wheel, and a block that took one and not the other would show a hint the wheel then breaks. Copy Chat's rows tell where the chat is drawn, so both read the same rows. Escape puts a block back where it was, and one that came from the stack takes its old place there, because a group stacks its blocks in the features' order. The input has focus the whole time the chat is open, and its arrow keys move the cursor and recall sent lines, so the arrows nudge a block only while the mouse holds one: a player pressing a button on the HUD is not editing text. A feature with nothing to show stands in while the chat is open, after the blocks with something to show, so opening the chat moves no live block. A drag event carries the keys held at its press, so Shift is asked of the keyboard, and pressing it mid-drag frees the block, in the editor too.

A docked block is dragged like any other, and the drag writes the feature's own Placement setting, so the setting stays the one place the dock is kept and the settings screen never disagrees with the HUD. Leaving the dock saves a scale that draws the block at the size the dock gave it, since a block that jumped in size under the mouse would land somewhere the player did not aim. The wheel takes a docked block out of the dock, because the dock draws at the hotbar's size and has no other.

## The HUD's scale snaps to screen pixels

The kit's edges are one pixel wide, and the HUD is the one place a player scales freely. At a scale of 1.3 a one-pixel edge covers 2.6 screen pixels at GUI scale 2, so some edges come out two pixels wide and others three, and the frame looks broken. `Readouts` therefore rounds a block's scale, times the HUD's, to a whole number of screen pixels per HUD pixel. The editor steps by one screen pixel for the same reason: a smaller step would change the saved number and draw nothing new.

A block's saved place is the corner of its plate, so a block stays where the player dropped it whether the plate is shown or not.

## World marks reach Minecraft once per tick

`World` draws through Minecraft's gizmos, the shapes its own debug views use. It submits them at the end of each client tick, and Minecraft replays the tick's gizmos on every frame until the next one, so a mark costs nothing in the frame loop.

Etherwarp Outline's box is the one mark drawn per frame, because it follows the crosshair and a box from the last tick trails a turning view by up to 50 ms. It is drawn from Fabric's `AFTER_BLOCK_OUTLINE_EXTRACTION`, which runs on the client thread while the frame's gizmo collector is open and before Minecraft hands that collector's shapes to the renderer. The line is followed again only when the view or the tick changed, and `PerfTest` caps it. A gizmo made outside that window goes to a collector that drops it without a word. Fabric's `takeScreenshot` draws a frame of its own outside the game's frame loop, so no collector is open there, and a game test that screenshots a per-frame shape opens the frame's collector itself. That is why a per-frame shape once looked as if it drew nothing.

## Rarity marks are baked from one color

A rarity style has glows that change opacity pixel by pixel, which as fills would be a few hundred rectangles per slot and thousands per chest, every frame. So `SlotArt` in `core` paints each rarity's 18x18 once, from the rarity's color alone, and `SlotArts` keeps one atlas per surface that it paints again only when the style changes. A marked slot then costs one blit, and the slots of a chest batch into one draw because they share the texture.

The art depends on the slot under it: a faint tint vanishes on Minecraft's gray and a dim one sinks into a dark resource pack's black. The mod can't see the pixels under a slot, so it reads the chest texture's slot from the selected resource packs when a container screen opens and the packs have changed. The hotbar lies over the world, so its art is always the dark kind.

Fabric attaches HUD elements before or after the whole hotbar, so the art behind the hotbar's items comes from `HudHotbarMixin`, which runs as vanilla is about to draw each item, and the gems come after it as a HUD element.
