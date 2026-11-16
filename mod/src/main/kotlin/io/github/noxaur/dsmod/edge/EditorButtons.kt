package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.edge.ui.Kit
import net.minecraft.client.gui.GuiGraphicsExtractor

/** The buttons under a settings editor: where each sits, how one is drawn for the mouse, and which one a point is on. */
class EditorButtons(private val labels: List<String>) {
    private val spots = IntArray(labels.size * 2)

    fun place(i: Int, x: Int, width: Int) {
        spots[i * 2] = x
        spots[i * 2 + 1] = width
    }

    fun x(i: Int) = spots[i * 2]

    fun width(i: Int) = spots[i * 2 + 1]

    fun middle(i: Int) = x(i) + width(i) / 2

    /** Whether the point is on button [i], whose top is [y]. */
    fun at(i: Int, y: Int, px: Double, py: Double) = px >= x(i) && px < x(i) + width(i) && py >= y && py < y + Kit.BUTTON_HEIGHT

    fun draw(g: GuiGraphicsExtractor, i: Int, y: Int, mouseX: Double, mouseY: Double, active: Boolean) {
        val over = at(i, y, mouseX, mouseY)
        val label = labels[i]
        Kit.button(g, x(i), y, width(i), Kit.BUTTON_HEIGHT, if (!active) Kit.State.OFF else if (over) Kit.State.HOVER else Kit.State.IDLE, label, Kit.font.width(label))
        if (over && active) g.requestCursor(CursorTypes.POINTING_HAND)
    }
}
