package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.text.amount
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.ui.EditorScreen
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitStepper
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW
import net.minecraft.client.input.MouseButtonEvent

private const val TITLE = "HUD Editor"
private const val HINT = "Drag or nudge with arrows, scroll or +/- to resize, right-click to reset."
private const val SCALE = "Scale"
private const val HUD_SCALE = "HUD Scale"
private const val VALUE_WIDTH = 30

/**
 * Drag a block or nudge it with the arrows to move it, into or out of a dock beside the hotbar, scroll over it to
 * resize it and right-click it to reset it, by [HudMover]'s rules, which the chat screen shares. The panel at the bottom works on the last block clicked or scrolled, and on the whole HUD.
 * Closing saves through [onSave] and goes back to [parent].
 */
class HudEditorScreen(
    private val readouts: Readouts,
    private val settings: DsModConfig.Hud,
    parent: Screen?,
    onSave: () -> Unit,
) : EditorScreen(TITLE, parent, onSave) {
    private val mover = HudMover(readouts)
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
        panelHeight = Kit.INSET * 2 + Kit.BUTTON_HEIGHT * 3 + Kit.LABEL_HEIGHT + Kit.GAP * 3
        panelX = (width - panelWidth) / 2
        panelY = height - panelHeight - Kit.GAP
        labelX = panelX + Kit.INSET
        val top = panelY + Kit.INSET
        nameY = top + Kit.BUTTON_HEIGHT + Kit.GAP
        scaleY = nameY + Kit.LABEL_HEIGHT + Kit.GAP
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
        super.removed()
    }

    // The HUD under this screen is what the player is editing, so nothing dims or blurs it.
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hovered = mover.held?.let(Hud::find) ?: if (inPanel(mouseX.toDouble(), mouseY.toDouble())) null else Hud.at(mouseX.toDouble(), mouseY.toDouble())
        hovered?.let { mover.ring(graphics, it, Kit.theme.accent, grabbable = true) }
        selected?.let(Hud::find)?.let { if (it !== hovered) mover.ring(graphics, it, Kit.theme.accent, grabbable = false) }

        Kit.panel(graphics, panelX, panelY, panelWidth, panelHeight)
        Kit.label(graphics, TITLE, labelX, panelY + Kit.INSET + (Kit.BUTTON_HEIGHT - Kit.LABEL_HEIGHT) / 2)
        Kit.label(graphics, name, labelX, nameY, if (selected == null) Kit.Ink.MUTED else Kit.Ink.NORMAL)
        Kit.label(graphics, SCALE, labelX, scaleY + (Kit.BUTTON_HEIGHT - Kit.LABEL_HEIGHT) / 2, if (scale.active) Kit.Ink.NORMAL else Kit.Ink.OFF)
        Kit.label(graphics, HUD_SCALE, labelX, hudScaleY + (Kit.BUTTON_HEIGHT - Kit.LABEL_HEIGHT) / 2)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (inPanel(event.x, event.y)) {
            super.mouseClicked(event, doubleClick)
            return true
        }
        val block = Hud.at(event.x, event.y) ?: return super.mouseClicked(event, doubleClick)
        when (event.button()) {
            0 -> mover.grab(block, event.x, event.y)
            1 -> mover.reset(block)
            else -> return super.mouseClicked(event, doubleClick)
        }
        select(block.id)
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (!mover.drag(event.x, event.y)) return super.mouseDragged(event, dx, dy)
        refresh()
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        mover.release()
        return super.mouseReleased(event)
    }

    // Escape during a drag puts the block back, and otherwise closes the editor. The arrows nudge the block held, or
    // else the one picked last, and + and - resize it, and the arrows move between the panel's controls when there is
    // none. The chat leaves + and - out, since they type there.
    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape && mover.cancel()) {
            refresh()
            return true
        }
        val block = (mover.held ?: selected)?.let(Hud::find)
        val grow = when (event.key()) {
            GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> 1.0
            GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> -1.0
            else -> 0.0
        }
        if (block != null && (grow != 0.0 || mover.nudge(block, event))) {
            mover.scroll(block, grow)
            refresh()
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0 || inPanel(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        val block = Hud.at(mouseX, mouseY) ?: return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        mover.scroll(block, scrollY)
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

    private fun inPanel(x: Double, y: Double) = x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + panelHeight

    private fun times(scale: Float) = amount(scale.toDouble(), 2, trim = true) + "x"
}
