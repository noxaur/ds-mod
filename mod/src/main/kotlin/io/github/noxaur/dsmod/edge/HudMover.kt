package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.config.DsModConfig.Position
import io.github.noxaur.dsmod.core.hud.HudBox
import io.github.noxaur.dsmod.core.hud.HudDrag
import io.github.noxaur.dsmod.edge.ui.Kit
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Moves and resizes HUD blocks with the mouse, the same in the HUD editor and on the chat screen: a drag moves a
 * block by `HudDrag`'s rules, Escape during one puts it back, the wheel resizes it and a right press resets it.
 */
class HudMover(private val readouts: Readouts) {
    private var drag: HudDrag? = null
    private var before: Position? = null

    /** The id of the block the mouse holds. */
    val held: String? get() = drag?.id

    /** Starts a drag on [block], pressed at [x], [y]. */
    fun grab(block: Hud.Placed, x: Double, y: Double) {
        drag = HudDrag(block.box(), x.toFloat(), y.toFloat())
        before = readouts.position(block.id)
    }

    /**
     * Moves the held block with the mouse at [x], [y], lined up unless Shift is held. False when the mouse holds none.
     * Shift is asked of the keyboard, because a drag event carries the keys held at the press.
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
        val to = held.to(block.box(), x.toFloat(), y.toFloat(), others, window.guiScaledWidth.toFloat(), window.guiScaledHeight.toFloat(), free = client.hasShiftDown())
        readouts.place(held.id, to.x, to.y)
        return true
    }

    /** Lets go of the held block where it is. False when the mouse holds none. */
    fun release(): Boolean = (drag != null).also { drag = null }

    /** Puts the held block back where it was before the drag. False when the mouse holds none. */
    fun cancel(): Boolean {
        val held = drag ?: return false
        drag = null
        readouts.restore(held.id, before)
        return true
    }

    /** Resizes [block] one step for each notch of the wheel. */
    fun scroll(block: Hud.Placed, by: Double) {
        if (by != 0.0) readouts.resize(block, if (by > 0) 1 else -1)
    }

    /** Puts [block] back in the stack at its own scale of 1. */
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
}
