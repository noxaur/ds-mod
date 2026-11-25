package io.github.noxaur.dsmod.core.feature.hud.layout

import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.config.Opens
import io.github.noxaur.dsmod.core.feature.hud.layout.hideinf3.HideInF3
import io.github.noxaur.dsmod.core.feature.hud.layout.hudbackground.HudBackground

internal fun Groups.layout() = group("Layout") {
    val hud = c.hud
    action(Opens.HUD_EDITOR, "Move HUD", "Move your HUD over the game, with the world behind it.", button = "Move", id = "moveHud")
    panel(hud.positions.bind, "Layout", "Drag a block to move it, and scroll over it to resize it.", Canvas.HUD_LAYOUT, id = "hudLayout") {
        if (it.isEmpty()) "Stacked" else if (it.size == 1) "1 block moved" else "${it.size} blocks moved"
    }
    number(hud.scale.bind, "Scale", "Make every HUD block bigger or smaller.", 0.5f..3f, step = 0.25f, unit = "x", id = "hudScale")
    feature(HudBackground(c))
    keys(hud.editKey.bind, "HUD Editor Key", "Pick a key that opens the HUD editor, or none.", id = "hudEditKey")
    feature(HideInF3(c))
}
