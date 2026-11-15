package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.hud.HudSnap
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW

private const val SNAP_REACH = 5f
private const val SNAP_MARGIN = 2f

// A press in the chat screen moves a block only once the mouse has gone this far, in GUI pixels, so a click stays a click.
private const val CHAT_SLOP = 3.0

/**
 * A HUD block the player drags, in the HUD editor or with the chat open. It lines up with the screen's edges and center
 * and with the other blocks when it comes near one, unless [free].
 */
class HudDrag(private val readouts: Readouts) {
    var id: String? = null
        private set
    private var grabX = 0.0
    private var grabY = 0.0

    // Where the block was before the drag, or null when the player had not placed it, for a cancel to put back.
    private var origin: Pair<Float, Float>? = null

    fun grab(block: Hud.Placed, x: Double, y: Double) {
        id = block.id
        origin = if (readouts.isPlaced(block.id)) block.x to block.y else null
        grabX = x - block.x
        grabY = y - block.y
    }

    /** Moves the block so the point it was grabbed at is under the mouse at [mouseX], [mouseY] on a screen this size. */
    fun move(mouseX: Double, mouseY: Double, free: Boolean, width: Int, height: Int) {
        val id = id ?: return
        var x = (mouseX - grabX).toFloat()
        var y = (mouseY - grabY).toFloat()
        val block = Hud.find(id)
        if (block != null && !free) {
            val others = Hud.placed().filter { it.id != id }
            x = HudSnap.snap(
                x, block.width * block.scale,
                others.flatMap { listOf(it.x, it.x + it.width * it.scale) } + SNAP_MARGIN + (width - SNAP_MARGIN),
                others.map { it.x + it.width * it.scale / 2 } + width / 2f,
                SNAP_REACH,
            )
            y = HudSnap.snap(
                y, block.height * block.scale,
                others.flatMap { listOf(it.y, it.y + it.height * it.scale) } + SNAP_MARGIN + (height - SNAP_MARGIN),
                others.map { it.y + it.height * it.scale / 2 } + height / 2f,
                SNAP_REACH,
            )
        }
        readouts.place(id, x, y)
    }

    /** Puts the block back where it was before the drag. */
    fun cancel() {
        val id = id ?: return
        val back = origin
        if (back == null) readouts.reset(id) else readouts.place(id, back.first, back.second)
        drop()
    }

    fun drop() {
        id = null
    }

    companion object {
        /**
         * With the chat open the mouse is free, so a block can be dragged there as in the HUD editor. The press still
         * reaches the chat, so a click on a line under a block works, and only a press that moves past [CHAT_SLOP]
         * picks the block up. Releasing it saves through [onSave].
         */
        fun register(readouts: Readouts, onSave: () -> Unit) {
            val drag = HudDrag(readouts)
            var pressed: Hud.Placed? = null
            var pressX = 0.0
            var pressY = 0.0
            ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
                if (screen !is ChatScreen) return@register
                ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                    pressed = if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && DsModClient.active && !client.gui.hud.isHidden) Hud.at(event.x, event.y) else null
                    pressX = event.x
                    pressY = event.y
                    true
                }
                ScreenMouseEvents.allowMouseDrag(screen).register { _, event, _, _ ->
                    val block = pressed
                    if (drag.id == null && block != null && Math.hypot(event.x - pressX, event.y - pressY) >= CHAT_SLOP) drag.grab(block, pressX, pressY)
                    if (drag.id == null) return@register true
                    drag.move(event.x, event.y, event.hasShiftDown(), screen.width, screen.height)
                    false
                }
                ScreenMouseEvents.allowMouseRelease(screen).register { _, _ ->
                    pressed = null
                    if (drag.id == null) return@register true
                    drag.drop()
                    onSave()
                    false
                }
                ScreenEvents.remove(screen).register {
                    pressed = null
                    if (drag.id != null) {
                        drag.drop()
                        onSave()
                    }
                }
            }
        }
    }
}
