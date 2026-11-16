package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.DsModConfig.Position
import io.github.noxaur.dsmod.core.hud.HudBox
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudDrag
import io.github.noxaur.dsmod.core.hud.HudDrop
import io.github.noxaur.dsmod.edge.ui.Kit
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent

/**
 * Moves and resizes HUD blocks with the mouse and the arrow keys, the same in the HUD editor and on the chat screen:
 * a drag moves a block by `HudDrag`'s rules and docks it beside the hotbar or undocks it, Escape during one puts it
 * back, an arrow nudges it, the wheel resizes it and a right press resets it.
 */
class HudMover(private val readouts: Readouts) {
    private var drag: HudDrag? = null
    private var before: Position? = null
    private var beforeDock = HudDock.FREE

    /** The id of the block the mouse holds. */
    val held: String? get() = drag?.id

    /** Starts a drag on [block], pressed at [x], [y]. */
    fun grab(block: Hud.Placed, x: Double, y: Double) {
        drag = HudDrag(block.box(), x.toFloat(), y.toFloat())
        before = readouts.position(block.id)
        beforeDock = readouts.dockOf(block.id)
    }

    /**
     * Moves the held block with the mouse at [x], [y], lined up and docked unless Shift is held. False when the mouse
     * holds none. Shift is asked of the keyboard, because a drag event carries the keys held at the press.
     */
    fun drag(x: Double, y: Double): Boolean {
        val held = drag ?: return false
        // A block its feature hid for a moment is still held, and moves again once it is back.
        val block = Hud.find(held.id) ?: return true
        val client = Minecraft.getInstance()
        val window = client.window
        val placed = Hud.placed()
        val others = ArrayList<HudBox>(placed.size)
        for (other in placed) others += other.box()
        val drop = held.to(
            block.box(), x.toFloat(), y.toFloat(), others, window.guiScaledWidth.toFloat(), window.guiScaledHeight.toFloat(),
            free = client.hasShiftDown(), docks = readouts.dockSpots(held.id),
        )
        put(block, drop)
        return true
    }

    /** Lets go of the held block where it is. False when the mouse holds none. */
    fun release(): Boolean = (drag != null).also { drag = null }

    /** Puts the held block back where it was before the drag, docked if it was. False when the mouse holds none. */
    fun cancel(): Boolean {
        val held = drag ?: return false
        drag = null
        readouts.restore(held.id, before, beforeDock)
        return true
    }

    /**
     * Nudges [block] with an arrow [event], one pixel or with Shift ten, and the held block keeps that much off the
     * mouse. False for any other key.
     */
    fun nudge(block: Hud.Placed, event: KeyEvent): Boolean {
        val step = if (Minecraft.getInstance().hasShiftDown()) HudDrag.NUDGE_FAR else HudDrag.NUDGE
        val dx = if (event.isLeft) -step else if (event.isRight) step else 0f
        val dy = if (event.isUp) -step else if (event.isDown) step else 0f
        if (dx == 0f && dy == 0f) return false
        val window = Minecraft.getInstance().window
        val to = HudDrag.nudged(block.box(), dx, dy, window.guiScaledWidth.toFloat(), window.guiScaledHeight.toFloat())
        drag?.takeIf { it.id == block.id }?.nudge(to.x - block.x, to.y - block.y)
        put(block, to)
        return true
    }

    /** Resizes [block] one step for each notch of the wheel. */
    fun scroll(block: Hud.Placed, by: Double) {
        if (by != 0.0) readouts.resize(block, if (by > 0) 1 else -1)
    }

    /** Puts [block] back in the stack at its own scale of 1, undocked. */
    fun reset(block: Hud.Placed) = readouts.reset(block.id)

    /** Rings [block] in [color]. Under the mouse, [grabbable] also shows the cursor that says it can be grabbed. */
    fun ring(graphics: GuiGraphicsExtractor, block: Hud.Placed, color: Int, grabbable: Boolean) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(block.x, block.y)
        pose.scale(block.scale, block.scale)
        Kit.ring(graphics, 0, 0, block.width, block.height, color)
        pose.popMatrix()
        if (grabbable) graphics.requestCursor(CursorTypes.RESIZE_ALL)
    }

    private fun put(block: Hud.Placed, drop: HudDrop) {
        if (drop.dock != HudDock.FREE) {
            if (block.dock != drop.dock) readouts.dock(block.id, drop.dock)
        } else {
            readouts.place(block.id, drop.x, drop.y)
        }
    }
}
