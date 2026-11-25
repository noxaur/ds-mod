# UI

How the mod draws what features hand it.

## A feature states a role, the text layer owns the color

Minecraft mods usually write color codes inline, and their colors drift from feature to feature. In ds-mod a feature says what a piece of text means, such as body, value or muted, and the text layer maps that role to a color, so the palette lives in one place. Text the server sent is a separate type that keeps the server's colors, so they can't leak into what the mod shows.

## UI layers keep what they are given

Each surface has one layer that draws a plain model. A model is handed over when something changes, and the layer converts it to Minecraft's types once and redraws it from then on. A per-frame hook was rejected, because a feature that built its text every frame would allocate in the render loop, where a dropped frame is a bug.

## Chrome is drawn in code, from a theme

Panels, buttons, slots and bars are a few filled rectangles each, drawn by the kit in a theme's colors. Minecraft's nine-slice textures were rejected, because a second look would need a second set of textures drawn by hand. In code a theme is one list of colors and a shape is a function. Only the icons are textures, white so one sprite takes any color.

## Two themes, and role text only on dark ground

Two themes ship, because resource-pack screens are dark and Minecraft's own are light gray. The role colors were picked for chat and the HUD and wash out on gray, so a label on a panel is plain text in the theme's ink, and role text sits only on dark ground, which stays dark in every theme.

## An item on the HUD is drawn from the mod's own record of it

The mod keeps how an item looks as well as what features read, and the HUD rebuilds the item from that alone. Asking for the stack the item was read from was rejected, because the game lets go of it once its menu closes, and the icon fell back to a default head.

## The chat keeps what it draws over the HUD

On the chat screen a press or a wheel turn over the chat's rows, input or suggestions is the chat's, even over a block, so a block is the mouse's only where the player sees it. A docked block's drag writes its feature's placement setting, so the setting stays the one place the dock is kept and the settings screen never disagrees with the HUD.

## The HUD's scale snaps to screen pixels

The kit's edges are one pixel wide, and at a free scale some edges come out two pixels wide and others three, so the frame looks broken. So a block's scale rounds to a whole number of screen pixels per HUD pixel, and the editor steps by one screen pixel. A block docked beside the hotbar shrinks to the room there by the same steps, so it never covers the hotbar.

## World marks reach Minecraft once per tick

World marks are submitted once per tick, and Minecraft replays them every frame, so a mark costs nothing in the frame loop. Only a mark that follows the crosshair or a moving entity is placed per frame, because a mark from the last tick would trail it by up to 50 ms. Labels and beams are the other exception: a label keeps its size on screen as the camera moves, and a gizmo's text can't be outlined, and a beam starts where it clears its label, so it moves with the camera too. Placing beams per tick was rejected, because for up to 50 ms after a turn they crossed their labels.

## World text is outlined like a glowing sign

Every text the mod draws in the world, such as a waypoint's name or Etherwarp's "No Room", is traced in its own color darkened to 40%, as vanilla traces a sign dyed with a glow ink sac, with no plate behind it. Text on the HUD and on screens keeps vanilla's drop shadow, so the outline marks text that floats in the world, as it does in vanilla. A plate behind the text, as on a name tag or the HUD, was rejected in favor of the outline, which reads on sky and ground alike with nothing behind it. Vanilla's faint name tag plate also left the muted distance unreadable against the sky.

## A waypoint is drawn in the color its player picked

A world mark is colored by its role, as text is, except a waypoint, whose look the player edits in depth. It carries the color and opacity they picked. The opacity is the box's fill, and the outline, beam and label are solid in the same color, so a see-through box never hides the name. A waypoint colored only by role was rejected, because the player asked to style each one.

## Rarity marks are baked once

A mark's alpha varies pixel by pixel, which as fills would be thousands of rectangles per chest every frame. So each rarity's art, with a row per sparkle placement that a slot's index picks, is painted once into a texture and a slot costs one blit. Opacity is the blit's color, so its slider repaints nothing. The art matches the slot under it, Menu Theme's or the resource pack's, because a faint tint vanishes on gray and a dim one sinks into black.

## A cooldown is drawn from its start and end

A slot's cooldown is handed over as its start and end, and the layer works out each frame's wash from the clock. Handing over the wash itself was rejected, because readouts refresh four times a second and the wash would stutter.

## A floating text is edited once, when its text arrives

A crowded fight floats dozens of damage numbers a second, each drawn every frame. So a feature's edit is decided once, when the server's text reaches the display, and a frame pays one field read. Rewriting in the renderer was rejected, because it would run the parser per display per frame. A hidden text is culled, not emptied, because a text display draws its background with no text in it.
