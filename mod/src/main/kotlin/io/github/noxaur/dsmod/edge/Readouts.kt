package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.DsModConfig.Position
import io.github.noxaur.dsmod.core.feature.Alert
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.Notice
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.HudScale
import io.github.noxaur.dsmod.core.hud.hud
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import kotlin.math.roundToInt

private const val MARGIN = 2
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 4f
private const val MIN_HUD_SCALE = 0.5f
private const val MAX_HUD_SCALE = 3f

/**
 * Hands each feature's readout to [Hud] and [Slots] when it changes, and what the features said to [Chat]
 * and [Alerts]. A message waits for the end of the tick, so it lands under the server line it answers. Placement lives here: a block the
 * player has moved has its own place, and the rest stack down from the top-left. A block's drawn scale is
 * its own times the HUD's, snapped to whole screen pixels.
 */
class Readouts(private val game: Game, private val settings: DsModConfig.Hud) {
    private val handed = IntArray(game.readouts.size) { -1 }
    private var width = 0
    private var height = 0
    private var guiScale = 0
    private var hudScale = 0f
    private var background = false

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
            // Slots drops every mark when the screen closes, and the hotbar still wants the inventory's.
            ScreenEvents.remove(screen).register { handed.fill(-1) }
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
        if (window.guiScaledWidth != width || window.guiScaledHeight != height || window.guiScale != guiScale ||
            settings.scale != hudScale || settings.background != background
        ) {
            width = window.guiScaledWidth
            height = window.guiScaledHeight
            guiScale = window.guiScale
            hudScale = settings.scale
            background = settings.background
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

    /** Moves the block of feature [id] so its corner is at a point in GUI pixels, and remembers it. */
    fun place(id: String, x: Float, y: Float) = save(id, x, y, scale(id))

    /** Makes [block] one screen pixel per HUD pixel bigger for each step [by], keeping its corner where it is. */
    fun resize(block: Hud.Placed, by: Int) {
        save(block.id, block.x, block.y, HudScale.step(scale(block.id), by, settings.scale, guiScale, MIN_SCALE, MAX_SCALE))
    }

    /** Makes the whole HUD one screen pixel per HUD pixel bigger for each step [by]. */
    fun resizeAll(by: Int) {
        settings.scale = HudScale.step(settings.scale, by, 1f, guiScale, MIN_HUD_SCALE, MAX_HUD_SCALE)
        push()
    }

    /** Forgets where the player put [id], so it goes back to the stack at its own scale of 1. */
    fun reset(id: String) {
        settings.positions.remove(id)
        handed.fill(-1)
        push()
    }

    fun isPlaced(id: String) = id in settings.positions

    /** The block's own scale, before the HUD's. */
    fun scale(id: String) = settings.positions[id]?.scale ?: 1f

    fun title(id: String) = game.readouts.first { it.feature.id == id }.feature.title

    private fun save(id: String, x: Float, y: Float, scale: Float) {
        settings.positions[id] = Position(x.coerceIn(0f, width.toFloat()) / width, y.coerceIn(0f, height.toFloat()) / height, scale)
        handed.fill(-1)
        push()
    }

    private fun group(id: String): HudGroup {
        val saved = settings.positions[id] ?: return HudGroup("stack", MARGIN, MARGIN, settings.background, HudScale.snap(settings.scale, guiScale))
        val scale = HudScale.snap(saved.scale * settings.scale, guiScale)
        return HudGroup(id, (saved.x * width).roundToInt(), (saved.y * height).roundToInt(), settings.background, scale)
    }
}
