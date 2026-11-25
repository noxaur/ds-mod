package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.Game
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.feature.hud.layout.hudbackground.HudBackground
import io.github.noxaur.dragen.core.hud.HudScale
import io.github.noxaur.dragen.core.text.text
import io.github.noxaur.dragen.edge.ui.EditorScreen
import io.github.noxaur.dragen.edge.ui.Kit
import io.github.noxaur.dragen.edge.ui.KitButton
import io.github.noxaur.dragen.edge.ui.KitCheckbox
import io.github.noxaur.dragen.edge.ui.KitStepper
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

private const val TITLE = "HUD Editor"
private const val HINT = "Drag or nudge with arrows, scroll or +/- to resize, right-click to reset."
private const val SCALE = "Scale"
private const val HUD_SCALE = "HUD Scale"
private const val VALUE_WIDTH = 30

class HudEditorScreen(
    private val game: Game,
    private val hudBackground: HudBackground,
    private val readouts: Readouts,
    private val settings: DragenConfig.Hud,
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
        val bottom = height - panelHeight - Kit.GAP
        panelY = if (covers(bottom)) Kit.GAP else bottom
        labelX = panelX + Kit.INSET
        val top = panelY + Kit.INSET
        nameY = top + Kit.BUTTON_HEIGHT + Kit.GAP
        scaleY = nameY + Kit.LABEL_HEIGHT + Kit.GAP
        hudScaleY = scaleY + Kit.BUTTON_HEIGHT + Kit.GAP
        val controlX = labelX + labelWidth + Kit.GAP

        addRenderableWidget(KitButton(panelX + panelWidth - Kit.INSET - doneWidth, top, "Done", ::onClose, doneWidth))
        scale = addRenderableWidget(KitStepper(controlX, scaleY, SCALE, VALUE_WIDTH) { by -> selected?.let(Hud::find)?.let { readouts.resize(it, by) }; refresh() })
        reset = addRenderableWidget(KitButton(scale.right + Kit.GAP, scaleY, "Reset", { selected?.let(readouts::reset); refresh(); clearPanel() }))
        hudScale = addRenderableWidget(KitStepper(controlX, hudScaleY, HUD_SCALE, VALUE_WIDTH) { by -> readouts.resizeAll(by); refresh() })
        background = addRenderableWidget(
            KitCheckbox(hudScale.right + Kit.GAP, hudScaleY + (Kit.BUTTON_HEIGHT - Kit.CHECKBOX_SIZE) / 2, "Background", hudBackground.feature.runs) {
                hudBackground.feature.enabled = it
            },
        ).apply { active = game.active }
        refresh()
    }

    override fun removed() {
        readouts.editing = false
        super.removed()
    }

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
            1 -> mover.reset(block).also { clearPanel() }
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
        if (mover.release()) clearPanel()
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape && mover.cancel()) {
            refresh()
            return true
        }
        val block = (mover.held ?: selected)?.let(Hud::find)
        val grow = Keys.resizeStep(event)
        if (block != null && (grow != 0 || mover.nudge(block, event))) {
            mover.scroll(block, grow.toDouble())
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
        scale.value = HudScale.label(id?.let(readouts::scale) ?: 1f)
        reset.active = id != null && readouts.isPlaced(id)
        hudScale.value = HudScale.label(settings.scale.value)
    }

    private fun covers(y: Int) = Hud.placed().any { it.x < panelX + panelWidth && it.x + it.width * it.scale > panelX && it.y < y + panelHeight && it.y + it.height * it.scale > y }

    private fun clearPanel() {
        if (covers(panelY)) rebuildWidgets()
    }

    private fun inPanel(x: Double, y: Double) = x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + panelHeight
}
