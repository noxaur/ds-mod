package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.DsModConfig.Position
import io.github.noxaur.dsmod.core.feature.Alert
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.Notice
import io.github.noxaur.dsmod.core.feature.hud.HudBackground
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.hud.HudBox
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.hud.HudScale
import io.github.noxaur.dsmod.core.hud.hud
import io.github.noxaur.dsmod.core.text.text
import kotlin.math.roundToInt
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

private const val MARGIN = 2
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 4f
private const val MIN_HUD_SCALE = 0.5f
private const val MAX_HUD_SCALE = 3f

class Readouts(private val game: Game, private val settings: DsModConfig.Hud, private val hudBackground: HudBackground) {
    private val handed = IntArray(game.readouts.size) { -1 }
    private var width = 0
    private var height = 0
    private var guiScale = 0
    private var hudScale = 0f
    private var background = false

    private var mainHand: Any? = null
    private var indicator: Any? = null

    var editing = false
        set(value) {
            field = value
            repaint()
        }

    var placeholders = false
        set(value) {
            if (field == value) return
            field = value
            for (i in handed.indices) if (game.readouts[i].hud == null) handed[i] = -1
            push()
        }

    val standIns = HashSet<String>()

    // @keep The world layer drops every mark with the old world, so each feature's marks are handed again, even unchanged.
    fun worldChanged() {
        World.worldChanged()
        handed.fill(-1)
    }

    fun opened(screen: AbstractContainerScreen<*>) {
        game.readouts.forEach { Slots.clear(it.feature.id) }
        handed.fill(-1)
        ScreenEvents.remove(screen).register { handed.fill(-1) }
    }

    fun push() {
        for (message in game.messages) {
            when (message) {
                is Notice -> Chat.notice(message.feature, message.detail)
                is Alert -> Alerts.title(message.title, Chat.noticeText(message.feature, message.detail))
            }
        }
        game.messages.clear()
        val client = Minecraft.getInstance()
        val window = client.window
        val plated = hudBackground.feature.runs
        if (window.guiScaledWidth != width || window.guiScaledHeight != height || window.guiScale != guiScale ||
            settings.scale.value != hudScale || plated != background ||
            client.options.mainHand().get() !== mainHand || client.options.attackIndicator().get() !== indicator
        ) {
            mainHand = client.options.mainHand().get()
            indicator = client.options.attackIndicator().get()
            width = window.guiScaledWidth
            height = window.guiScaledHeight
            guiScale = window.guiScale
            hudScale = settings.scale.value
            background = plated
            handed.fill(-1)
        }
        for (i in handed.indices) {
            val readout = game.readouts[i]
            if (handed[i] == readout.version) continue
            handed[i] = readout.version
            val feature = readout.feature
            val id = feature.id
            val block = readout.hud ?: when {
                editing && feature.hasHud -> hud(feature.title) {}
                placeholders && feature.hasHud && feature.on -> HudBlock(text { muted(feature.title) }, emptyList(), HudLook.CLEAR)
                else -> null
            }
            if (readout.hud == null && block != null) standIns += id else standIns -= id
            val order = if (readout.hud == null) handed.size + i else i
            if (block == null) Hud.hide(id) else Hud.show(id, group(id, feature.placement?.value ?: block.dock), block, order = order)
            if (readout.slots.isEmpty()) Slots.clear(id) else Slots.mark(id, readout.slots)
            if (readout.marks.isEmpty()) World.hide(id) else World.show(id, readout.marks)
        }
    }

    fun place(id: String, x: Float, y: Float) = save(id, x, y, scale(id))

    fun resize(block: Hud.Placed, by: Int) {
        val scale = scale(block.id)
        val next = HudScale.step(scale, by, settings.scale.value, guiScale, MIN_SCALE, MAX_SCALE)
        if (next == scale) return
        val drawn = HudScale.snap(next * settings.scale.value, guiScale)
        save(block.id, minOf(block.x, width - block.width * drawn), minOf(block.y, height - block.height * drawn), next)
    }

    fun dock(id: String, dock: HudDock) {
        feature(id).placement?.value = dock
        repaint()
    }

    fun dockOf(id: String): HudDock = feature(id).placement?.value ?: HudDock.FREE

    fun dockSpots(id: String): Map<HudDock, HudBox> {
        if (feature(id).placement == null) return emptyMap()
        val scale = HudScale.snap(1f, guiScale)
        val spots = HashMap<HudDock, HudBox>()
        for (dock in HudDock.entries) if (dock != HudDock.FREE) Hud.dockSpot(id, dock, scale)?.let { spots[dock] = it }
        return spots
    }

    fun resizeAll(by: Int) {
        settings.scale.value = HudScale.step(settings.scale.value, by, 1f, guiScale, MIN_HUD_SCALE, MAX_HUD_SCALE)
        push()
    }

    fun reset(id: String) = restore(id, null, HudDock.FREE)

    fun position(id: String): Position? = settings.positions.value[id]

    fun restore(id: String, position: Position?, dock: HudDock) {
        settings.positions.value = if (position == null) settings.positions.value - id else settings.positions.value + (id to position)
        feature(id).placement?.value = dock
        repaint()
    }

    fun replace() = repaint()

    fun isPlaced(id: String) = id in settings.positions.value || dockOf(id) != HudDock.FREE

    fun scale(id: String) =
        if (dockOf(id) != HudDock.FREE) (Hud.find(id)?.scale ?: 1f) / settings.scale.value else settings.positions.value[id]?.scale ?: 1f

    fun title(id: String) = feature(id).title

    private fun feature(id: String) = game.readouts.first { it.feature.id == id }.feature

    private fun save(id: String, x: Float, y: Float, scale: Float) {
        settings.positions.value += id to Position(x.coerceIn(0f, width.toFloat()) / width, y.coerceIn(0f, height.toFloat()) / height, scale)
        feature(id).placement?.value = HudDock.FREE
        repaint()
    }

    private fun repaint() {
        handed.fill(-1)
        push()
    }

    private fun group(id: String, dock: HudDock): HudGroup {
        if (dock != HudDock.FREE) return HudGroup(id, 0, 0, background, HudScale.snap(1f, guiScale), dock)
        val saved = settings.positions.value[id] ?: return HudGroup("stack", MARGIN, MARGIN, background, HudScale.snap(settings.scale.value, guiScale))
        val scale = HudScale.snap(saved.scale * settings.scale.value, guiScale)
        return HudGroup(id, (saved.x * width).roundToInt(), (saved.y * height).roundToInt(), background, scale)
    }
}
