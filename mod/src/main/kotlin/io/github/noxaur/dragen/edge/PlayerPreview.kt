package io.github.noxaur.dragen.edge

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dragen.core.text.text
import io.github.noxaur.dragen.core.ui.Turntable
import io.github.noxaur.dragen.edge.ui.Box
import io.github.noxaur.dragen.edge.ui.BoxEditor
import io.github.noxaur.dragen.edge.ui.Kit
import io.github.noxaur.dragen.edge.ui.SettingRows
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.sqrt

class PlayerPreview : BoxEditor {
    override val box = Box()

    internal val turntable = Turntable(speed = 0f).apply { turn(START_YAW, 0f, 0L) }

    override val capturing get() = turntable.dragging


    override fun layout(x: Int, y: Int, width: Int): Int {
        box.place(x, y, width)
        box.height = HEIGHT
        return HEIGHT
    }

    override fun refresh() = Unit

    override fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, keys: Boolean): SettingRows.Tip? {
        val y = box.origin + box.top
        if (y + HEIGHT <= box.clipTop || y >= box.clipBottom) return null
        Kit.well(g, box.x, y, box.width, HEIGHT)
        val player = Minecraft.getInstance().player
        if (player == null) {
            val line = text { muted(OUTSIDE) }.toComponent()
            g.text(Kit.font, line, box.x + (box.width - Kit.font.width(line)) / 2, y + (HEIGHT - 8) / 2, -1, false)
            return null
        }
        val state = InventoryModel.state(player, 0f)
        val room = (HEIGHT - 4) / (state.boundingBoxHeight * MARGIN)
        val reach = maxOf(1f, PlayerModels.reach)
        val scale = minOf(room / sqrt(LARGEST * reach), room / reach)
        val tip = Quaternionf().rotateX(turntable.pitch * DEGREES)
        val rotation = Quaternionf().rotateZ(PI.toFloat()).mul(tip).rotateY(-turntable.yaw(0L) * DEGREES)
        val middle = Vector3f(0f, state.boundingBoxHeight * PlayerModels.tall / 2f + InventoryModel.Y_OFFSET, 0f)
        g.enableScissor(box.x + 1, maxOf(y + 1, box.clipTop), box.x + box.width - 1, minOf(y + HEIGHT - 1, box.clipBottom))
        g.entity(state, scale, middle, rotation, tip, box.x + 1, y + 1, box.x + box.width - 1, y + HEIGHT - 1)
        g.disableScissor()
        if (turntable.dragging || box.isOver(mouseX.toDouble(), mouseY.toDouble())) g.requestCursor(CursorTypes.RESIZE_ALL)
        return null
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!box.isOver(event.x, event.y) || Minecraft.getInstance().player == null) return false
        turntable.press(event.x, event.y, 0L)
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (!turntable.dragging) return false
        turntable.drag(event.x, event.y)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!turntable.dragging) return false
        turntable.release(0L)
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, shift: Boolean) = false

    override fun keyPressed(event: KeyEvent): Boolean = when {
        event.isEscape -> turntable.cancel(0L)
        event.isLeft -> true.also { turntable.turn(-Turntable.KEY_YAW, 0f, 0L) }
        event.isRight -> true.also { turntable.turn(Turntable.KEY_YAW, 0f, 0L) }
        else -> false
    }

    override fun leave() {
        turntable.cancel(0L)
    }

    override fun narrate(output: NarrationElementOutput) = false

    private companion object {
        const val HEIGHT = 120
        const val START_YAW = -30f
        const val MARGIN = 1.1f

        const val LARGEST = 3f
        const val DEGREES = (PI / 180).toFloat()
        const val OUTSIDE = "Join a world to see your model here."
    }
}
