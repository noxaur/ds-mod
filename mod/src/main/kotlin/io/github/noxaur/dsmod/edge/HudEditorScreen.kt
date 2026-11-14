package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.text.text
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent

/** Drag a block to move it and scroll over it to resize it. Closing saves through [onSave]. */
class HudEditorScreen(private val readouts: Readouts, private val onSave: () -> Unit) : Screen(text { body("HUD Editor") }.toComponent()) {
    private val hint = text { body("Drag to move, scroll to resize, Esc to save.") }.toComponent()
    private var grabbed: Hud.Placed? = null
    private var grabX = 0.0
    private var grabY = 0.0

    override fun init() {
        readouts.editing = true
    }

    override fun removed() {
        readouts.editing = false
        onSave()
    }

    // The HUD under this screen is what the player is editing, so nothing dims or blurs it.
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, hint, width / 2, height - 20, -1)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val block = Hud.at(event.x, event.y) ?: return super.mouseClicked(event, doubleClick)
        grabbed = block
        grabX = event.x - block.x
        grabY = event.y - block.y
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val block = grabbed ?: return super.mouseDragged(event, dx, dy)
        readouts.place(block.id, (event.x - grabX).toFloat(), (event.y - grabY).toFloat(), block.scale)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        grabbed = null
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val block = Hud.at(mouseX, mouseY) ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        readouts.place(block.id, block.x, block.y, (block.scale + scrollY.toFloat() * 0.1f).coerceIn(0.5f, 4f))
        return true
    }
}
