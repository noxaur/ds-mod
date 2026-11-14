package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig.Position
import io.github.noxaur.dsmod.core.feature.Alert
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.Notice
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.hud
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import kotlin.math.roundToInt

private const val MARGIN = 4

/**
 * Hands each feature's readout to [Hud] and [Slots] when it changes, and what the features said to [Chat]
 * and [Alerts]. A message waits for the end of the tick, so it lands under the server line it answers. Placement lives here: a block the
 * player has moved has its own place, and the rest stack down from the top-left.
 */
class Readouts(private val game: Game, private val positions: MutableMap<String, Position>) {
    private val handed = IntArray(game.readouts.size) { -1 }
    private var width = 0
    private var height = 0

    /** While the HUD editor is open, a feature with nothing to show still shows its header, so it can be placed. */
    var editing = false
        set(value) {
            field = value
            handed.fill(-1)
            push()
        }

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            // The marks are for the container the last poll saw, and this screen may show another. The next tick hands them over again.
            game.readouts.forEach { Slots.clear(it.feature.id) }
            handed.fill(-1)
        }
    }

    /** Call once a tick, after [Game.tick]. */
    fun push() {
        for (message in game.messages) {
            when (message) {
                is Notice -> Chat.notice(message.feature, message.detail)
                is Alert -> Alerts.title(message.title, Chat.noticeText(message.feature, message.detail))
            }
        }
        game.messages.clear()
        val window = Minecraft.getInstance().window
        if (window.guiScaledWidth != width || window.guiScaledHeight != height) {
            width = window.guiScaledWidth
            height = window.guiScaledHeight
            handed.fill(-1)
        }
        for (i in handed.indices) {
            val readout = game.readouts[i]
            if (handed[i] == readout.version) continue
            handed[i] = readout.version
            val id = readout.feature.id
            val block = readout.hud ?: if (editing && readout.feature.hasHud) hud(readout.feature.title) {} else null
            if (block == null) Hud.hide(id) else Hud.show(id, group(id), block)
            if (readout.slots.isEmpty()) Slots.clear(id) else Slots.mark(id, readout.slots)
        }
    }

    /** Moves the block of feature [id] so its top-left corner is at a point in GUI pixels, and remembers it. */
    fun place(id: String, x: Float, y: Float, scale: Float) {
        positions[id] = Position(x.coerceIn(0f, width.toFloat()) / width, y.coerceIn(0f, height.toFloat()) / height, scale)
        handed.fill(-1)
        push()
    }

    private fun group(id: String): HudGroup {
        val saved = positions[id] ?: return HudGroup("stack", MARGIN, MARGIN, editing)
        return HudGroup(id, (saved.x * width).roundToInt(), (saved.y * height).roundToInt(), editing, saved.scale)
    }
}
