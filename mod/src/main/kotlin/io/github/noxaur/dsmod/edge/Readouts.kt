package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.DsModConfig.Position
import io.github.noxaur.dsmod.core.feature.Alert
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.Notice
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

/**
 * Hands each feature's readout to [Hud], [Slots] and [World] when it changes, and what the features said to [Chat]
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

    // Where vanilla puts the offhand slot and the attack indicator, which a block docked beside the hotbar steps past.
    private var mainHand: Any? = null
    private var indicator: Any? = null

    /** While the HUD editor is open, a feature with nothing to show still shows its header, so it can be placed. */
    var editing = false
        set(value) {
            field = value
            handed.fill(-1)
            push()
        }

    /**
     * While the chat is open for Drag In Chat, a feature that is on and has nothing to show stands in as its title
     * alone, which the chat outlines, so it can be placed from there too. Only those blocks are handed over again.
     */
    var placeholders = false
        set(value) {
            if (field == value) return
            field = value
            for (i in handed.indices) if (game.readouts[i].hud == null) handed[i] = -1
            push()
        }

    /** The ids of the blocks standing in for a feature with nothing to show, while [placeholders] is on. */
    val standIns = HashSet<String>()

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
        val client = Minecraft.getInstance()
        val window = client.window
        if (window.guiScaledWidth != width || window.guiScaledHeight != height || window.guiScale != guiScale ||
            settings.scale != hudScale || settings.background != background ||
            client.options.mainHand().get() !== mainHand || client.options.attackIndicator().get() !== indicator
        ) {
            mainHand = client.options.mainHand().get()
            indicator = client.options.attackIndicator().get()
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
            val feature = readout.feature
            val id = feature.id
            val block = readout.hud ?: when {
                editing && feature.hasHud -> hud(feature.title) {}
                placeholders && feature.hasHud && feature.on -> HudBlock(text { muted(feature.title) }, emptyList(), HudLook.CLEAR)
                else -> null
            }
            if (readout.hud == null && block != null) standIns += id else standIns -= id
            // A block that stands in follows the ones with something to show, so those stay where they were.
            val order = if (readout.hud == null) handed.size + i else i
            if (block == null) Hud.hide(id) else Hud.show(id, group(id, feature.placement?.get() ?: block.dock), block, order = order)
            if (readout.slots.isEmpty()) Slots.clear(id) else Slots.mark(id, readout.slots)
            if (readout.marks.isEmpty()) World.hide(id) else World.show(id, readout.marks)
        }
    }

    /**
     * Moves the block of feature [id] so its corner is at a point in GUI pixels, and remembers it. A docked block
     * leaves the dock at the size it had there.
     */
    fun place(id: String, x: Float, y: Float) = save(id, x, y, scale(id))

    /**
     * Makes [block] one screen pixel per HUD pixel bigger for each step [by], keeping its corner where it is, or nearer
     * the top-left where the bigger block would run off the screen. A docked block is drawn at the hotbar's size, so it
     * leaves the dock to take another.
     */
    fun resize(block: Hud.Placed, by: Int) {
        val scale = scale(block.id)
        val next = HudScale.step(scale, by, settings.scale, guiScale, MIN_SCALE, MAX_SCALE)
        // A step that changes nothing, at a bound, leaves a docked block in its dock.
        if (next == scale) return
        val drawn = HudScale.snap(next * settings.scale, guiScale)
        save(block.id, minOf(block.x, width - block.width * drawn), minOf(block.y, height - block.height * drawn), next)
    }

    /** Docks the block of feature [id] at [dock] beside the hotbar, as its placement setting does. */
    fun dock(id: String, dock: HudDock) {
        feature(id).placement?.set(dock)
        handed.fill(-1)
        push()
    }

    /** Where the block of feature [id] is docked, or [HudDock.FREE]. */
    fun dockOf(id: String): HudDock = feature(id).placement?.get() ?: HudDock.FREE

    /** The boxes the block of feature [id] would fill in each dock spot, or none for a feature that does not dock. */
    fun dockSpots(id: String): Map<HudDock, HudBox> {
        if (feature(id).placement == null) return emptyMap()
        val scale = HudScale.snap(1f, guiScale)
        val spots = HashMap<HudDock, HudBox>()
        for (dock in HudDock.entries) if (dock != HudDock.FREE) Hud.dockSpot(id, dock, scale)?.let { spots[dock] = it }
        return spots
    }

    /** Makes the whole HUD one screen pixel per HUD pixel bigger for each step [by]. */
    fun resizeAll(by: Int) {
        settings.scale = HudScale.step(settings.scale, by, 1f, guiScale, MIN_HUD_SCALE, MAX_HUD_SCALE)
        push()
    }

    /** Forgets where the player put [id] and undocks it, so it goes back to the stack at its own scale of 1. */
    fun reset(id: String) = restore(id, null, HudDock.FREE)

    /** Where the player put [id], or null when it sits in the stack. */
    fun position(id: String): Position? = settings.positions[id]

    /** Puts [id] back at a [position] it had, or in the stack for null, and in the [dock] it had. */
    fun restore(id: String, position: Position?, dock: HudDock) {
        if (position == null) settings.positions.remove(id) else settings.positions[id] = position
        feature(id).placement?.set(dock)
        handed.fill(-1)
        push()
    }

    /** Places every block again, after the saved places changed from outside, such as the layout's reset in the settings. */
    fun replace() {
        handed.fill(-1)
        push()
    }

    /** Whether the player moved [id] out of the stack, to a place or a dock, so a reset would change something. */
    fun isPlaced(id: String) = id in settings.positions || dockOf(id) != HudDock.FREE

    /**
     * The block's own scale, before the HUD's. A docked block's is the one it is drawn at there: the hotbar's size, or
     * smaller where the room beside the hotbar is narrower, so it leaves the dock and resizes from the size it shows.
     */
    fun scale(id: String) =
        if (dockOf(id) != HudDock.FREE) (Hud.find(id)?.scale ?: 1f) / settings.scale else settings.positions[id]?.scale ?: 1f

    fun title(id: String) = feature(id).title

    private fun feature(id: String) = game.readouts.first { it.feature.id == id }.feature

    private fun save(id: String, x: Float, y: Float, scale: Float) {
        settings.positions[id] = Position(x.coerceIn(0f, width.toFloat()) / width, y.coerceIn(0f, height.toFloat()) / height, scale)
        feature(id).placement?.set(HudDock.FREE)
        handed.fill(-1)
        push()
    }

    /** Where [id] goes. A block docked beside the hotbar is drawn at the GUI's scale, so its slots match the hotbar's. */
    private fun group(id: String, dock: HudDock): HudGroup {
        if (dock != HudDock.FREE) return HudGroup(id, 0, 0, settings.background, HudScale.snap(1f, guiScale), dock)
        val saved = settings.positions[id] ?: return HudGroup("stack", MARGIN, MARGIN, settings.background, HudScale.snap(settings.scale, guiScale))
        val scale = HudScale.snap(saved.scale * settings.scale, guiScale)
        return HudGroup(id, (saved.x * width).roundToInt(), (saved.y * height).roundToInt(), settings.background, scale)
    }
}
