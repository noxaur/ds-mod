package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitStepper
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import java.util.Locale
import kotlin.math.sign

private const val TITLE = "HUD Editor"
private const val HINT = "Drag to move, scroll to resize."
private const val SCALE = "Scale"
private const val HUD_SCALE = "HUD Scale"
private const val VALUE_WIDTH = 30
private const val LABEL_HEIGHT = 8

/**
 * Drag a block to move it and scroll over it to resize it. The panel at the bottom works on the last block
 * clicked or scrolled, and on the whole HUD. Closing saves through [onSave].
 */
class HudEditorScreen(
    private val readouts: Readouts,
    private val settings: DsModConfig.Hud,
    private val onSave: () -> Unit,
) : Screen(text { body(TITLE) }.toComponent()) {
    private var grabbed: String? = null
    private var grabX = 0.0
    private var grabY = 0.0
    private var selected: String? = null

    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var labelX = 0
    private var nameY = 0
    private var scaleY = 0
    private var hudScaleY = 0
    private var name = HINT
    private lateinit var scale: KitStepper
    private lateinit var reset: KitButton
    private lateinit var hudScale: KitStepper
    private lateinit var background: KitCheckbox

    override fun init() {
        readouts.editing = true
        val labelWidth = maxOf(font.width(SCALE), font.width(HUD_SCALE))
        val controls = labelWidth + Kit.GAP + Kit.BUTTON_HEIGHT * 2 + Kit.GAP * 2 + VALUE_WIDTH + Kit.GAP
        val doneWidth = font.width("Done") + 16
        val content = maxOf(controls + maxOf(font.width("Reset") + 16, Kit.CHECKBOX_SIZE + Kit.GAP + font.width("Background")), font.width(HINT))
        panelWidth = content + Kit.INSET * 2
        panelHeight = Kit.INSET * 2 + Kit.BUTTON_HEIGHT * 3 + LABEL_HEIGHT + Kit.GAP * 3
        panelX = (width - panelWidth) / 2
        panelY = height - panelHeight - Kit.GAP
        labelX = panelX + Kit.INSET
        val top = panelY + Kit.INSET
        nameY = top + Kit.BUTTON_HEIGHT + Kit.GAP
        scaleY = nameY + LABEL_HEIGHT + Kit.GAP
        hudScaleY = scaleY + Kit.BUTTON_HEIGHT + Kit.GAP
        val controlX = labelX + labelWidth + Kit.GAP

        addRenderableWidget(KitButton(panelX + panelWidth - Kit.INSET - doneWidth, top, "Done", ::onClose, doneWidth))
        scale = addRenderableWidget(KitStepper(controlX, scaleY, SCALE, VALUE_WIDTH) { by -> selected?.let(Hud::find)?.let { readouts.resize(it, by) }; refresh() })
        reset = addRenderableWidget(KitButton(scale.right + Kit.GAP, scaleY, "Reset", { selected?.let(readouts::reset); refresh() }))
        hudScale = addRenderableWidget(KitStepper(controlX, hudScaleY, HUD_SCALE, VALUE_WIDTH) { by -> readouts.resizeAll(by); refresh() })
        background = addRenderableWidget(
            KitCheckbox(hudScale.right + Kit.GAP, hudScaleY + (Kit.BUTTON_HEIGHT - Kit.CHECKBOX_SIZE) / 2, "Background", settings.background) {
                settings.background = it
            },
        )
        refresh()
    }

    override fun removed() {
        readouts.editing = false
        onSave()
    }

    // The HUD under this screen is what the player is editing, so nothing dims or blurs it.
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hovered = grabbed?.let(Hud::find) ?: if (inPanel(mouseX.toDouble(), mouseY.toDouble())) null else Hud.at(mouseX.toDouble(), mouseY.toDouble())
        hovered?.let { ring(graphics, it) }
        selected?.let(Hud::find)?.let { if (it !== hovered) ring(graphics, it) }

        Kit.panel(graphics, panelX, panelY, panelWidth, panelHeight)
        Kit.label(graphics, TITLE, labelX, panelY + Kit.INSET + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        Kit.label(graphics, name, labelX, nameY, if (selected == null) Kit.Ink.MUTED else Kit.Ink.NORMAL)
        Kit.label(graphics, SCALE, labelX, scaleY + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2, if (scale.active) Kit.Ink.NORMAL else Kit.Ink.OFF)
        Kit.label(graphics, HUD_SCALE, labelX, hudScaleY + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (inPanel(event.x, event.y)) {
            super.mouseClicked(event, doubleClick)
            return true
        }
        val block = Hud.at(event.x, event.y) ?: return super.mouseClicked(event, doubleClick)
        grabbed = block.id
        grabX = event.x - block.x
        grabY = event.y - block.y
        select(block.id)
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val id = grabbed ?: return super.mouseDragged(event, dx, dy)
        readouts.place(id, (event.x - grabX).toFloat(), (event.y - grabY).toFloat())
        refresh()
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        grabbed = null
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0 || inPanel(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val block = Hud.at(mouseX, mouseY) ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        readouts.resize(block, scrollY.sign.toInt())
        select(block.id)
        return true
    }

    private fun select(id: String) {
        selected = id
        refresh()
    }

    private fun refresh() {
        val id = selected
        name = id?.let(readouts::title) ?: HINT
        scale.active = id != null
        scale.value = times(id?.let(readouts::scale) ?: 1f)
        reset.active = id != null && readouts.isPlaced(id)
        hudScale.value = times(settings.scale)
    }

    private fun ring(graphics: GuiGraphicsExtractor, block: Hud.Placed) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(block.x, block.y)
        pose.scale(block.scale, block.scale)
        Kit.ring(graphics, 0, 0, block.width, block.height, Kit.theme.accent)
        pose.popMatrix()
    }

    private fun inPanel(x: Double, y: Double) = x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + panelHeight

    private fun times(scale: Float) = String.format(Locale.US, "%.2f", scale).trimEnd('0').trimEnd('.') + "x"
}
