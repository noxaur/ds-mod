package io.github.noxaur.dsmod.edge.ui

import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Narration
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.TabStrip
import io.github.noxaur.dsmod.edge.toComponent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.joml.Vector2i
import kotlin.math.abs

class KitTabBar(
    x: Int,
    y: Int,
    width: Int,
    private val tabs: SettingsTabs,
    private val names: List<String>,
    private val colors: List<Int>,
    private val onSelect: (Int) -> Unit,
    private val onClose: (Int) -> Unit,
    private val onNew: () -> Unit,
    private val onMove: (Int, Int) -> Unit,
    private val icons: List<Icon?> = emptyList(),
) : AbstractWidget(x, y, width, Kit.TAB_BAR_HEIGHT, text { body("Tabs") }.toComponent()) {
    private val strip = TabStrip()
    private val brandWidth = Kit.font.width(BRAND)
    private var labels = emptyArray<String>()

    private var cut = emptyArray<List<FormattedCharSequence>?>()

    private val below = ClientTooltipPositioner { screenWidth, _, mouseX, _, tipWidth, _ ->
        Vector2i((mouseX + TIP_X).coerceAtMost(screenWidth - tipWidth - TIP_X), y + height + TIP_Y)
    }

    private sealed interface Held {
        val tab: Int
        val grab: Int
    }

    private data class Pressed(override val tab: Int, override val grab: Int, val pressX: Double) : Held

    private data class Moving(override val tab: Int, override val grab: Int, val origin: Int, val x: Int) : Held

    private var held: Held? = null

    private var overTab = -1
    private var overClose = false
    private var overNewTab = false

    private val stripX get() = x + START
    private val lineY get() = y + height - 1
    private val brandX get() = x + width - END - brandWidth
    private val newX get() = stripX + strip.newX

    fun layout() {
        strip.layout(tabs.size, brandX - BRAND_GAP - stripX, tabs.selected)
        val close = tabs.canClose
        labels = Array(tabs.size) { i -> fit(tabs.tabs[i].title(names), Kit.pageTabLabel(strip.width, lead(i), close)).takeUnless { it == "..." && lead(i) } ?: "" }
        cut = Array(tabs.size) { i -> tabs.tabs[i].title(names).let { if (it == labels[i]) null else listOf(text { body(it) }.toComponent().visualOrderText) } }
    }

    fun cancelDrag(): Boolean {
        val moving = held as? Moving ?: return false
        if (moving.tab != moving.origin) onMove(moving.tab, moving.origin)
        held = null
        return true
    }

    private fun lead(i: Int) = tabs.tabs[i].place.let { it.searching || it.section >= 0 }

    private fun onClose(i: Int, mouseX: Double, mouseY: Double): Boolean {
        val cx = Kit.pageTabCloseX(stripX + strip.x(i), strip.width)
        val cy = Kit.pageTabCloseY(lineY)
        return tabs.canClose && mouseX >= cx && mouseX < cx + Kit.PAGE_TAB_LEAD && mouseY >= cy && mouseY < cy + Kit.PAGE_TAB_LEAD
    }

    private fun onNew(mouseX: Double) = mouseX >= newX && mouseX < newX + Kit.NEW_TAB_WIDTH

    private fun tabAt(mouseX: Double) = strip.at(mouseX.toInt() - stripX)

    override fun isValidClickButton(button: MouseButtonInfo) = button.button() == 0 || button.button() == MIDDLE

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        if (event.button() != 0 && held != null) return
        if (onNew(event.x)) {
            if (event.button() == 0) onNew()
            return
        }
        val i = tabAt(event.x).takeIf { it >= 0 } ?: return
        if (event.button() == MIDDLE || onClose(i, event.x, event.y)) return onClose(i)
        onSelect(i)
        held = Pressed(i, event.x.toInt() - stripX - strip.x(i), event.x)
    }

    override fun onDrag(event: MouseButtonEvent, dx: Double, dy: Double) {
        val x = event.x.toInt() - stripX
        val moving = when (val now = held) {
            null -> return
            is Pressed -> if (abs(event.x - now.pressX) < DRAG_START) return else Moving(now.tab, now.grab, now.tab, x)
            is Moving -> now.copy(x = x)
        }
        val to = strip.drop(x, moving.tab)
        if (to != moving.tab) onMove(moving.tab, to)
        held = moving.copy(tab = to)
    }

    override fun onRelease(event: MouseButtonEvent) {
        held = null
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!strip.overflows) return false
        val by = if (scrollX != 0.0) -scrollX else scrollY
        if (by == 0.0) return false
        strip.scroll(if (by > 0) -(strip.width + TabStrip.GAP) else strip.width + TabStrip.GAP)
        return true
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.tabBar(graphics, x, y, width)
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        val moving = held as? Moving
        val over = if (isHovered && moving == null) tabAt(mx) else -1
        val all = tabs.tabs
        graphics.enableScissor(stripX, y, stripX + strip.view, y + height)
        for (i in all.indices) {
            if (i == moving?.tab) continue
            tab(graphics, i, stripX + strip.x(i), if (i == over) Kit.State.HOVER else Kit.State.IDLE, i == over && onClose(i, mx, my))
        }
        if (moving != null) tab(graphics, moving.tab, stripX + strip.dragX(moving.x, moving.grab), Kit.State.DOWN, false)
        graphics.disableScissor()
        val overNew = isHovered && moving == null && onNew(mx)
        overTab = over
        overClose = over >= 0 && onClose(over, mx, my)
        overNewTab = overNew
        Kit.newTab(graphics, newX, lineY, if (overNew) Kit.State.HOVER else Kit.State.IDLE)
        Kit.tag(graphics, brandX, lineY, BRAND)
        val tip = if (overNew) NEW_TAB else cut.getOrNull(over)?.takeUnless { onClose(over, mx, my) }
        if (tip != null) graphics.setTooltipForNextFrame(Kit.font, tip, below, mouseX, mouseY, false)
    }

    private fun tab(graphics: GuiGraphicsExtractor, i: Int, tabX: Int, state: Kit.State, overClose: Boolean) {
        val place = tabs.tabs[i].place
        val selected = i == tabs.selected
        val close = when {
            !tabs.canClose || !selected && state != Kit.State.HOVER -> null
            overClose -> Kit.State.HOVER
            else -> Kit.State.IDLE
        }
        val icon = if (place.searching) Icon.SEARCH else icons.getOrNull(place.section)
        val swatch = if (icon == null && place.section >= 0) colors[place.section] else 0
        Kit.pageTab(graphics, tabX, lineY, strip.width, state, selected, labels[i], swatch, icon, close)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        val tab = overTab.takeIf { isHovered && it in tabs.tabs.indices }
        val said = when {
            isHovered && overNewTab -> Narration.join("New Tab", "button")
            tab != null && overClose -> Narration.join("Close ${tabs.tabs[tab].title(names)}", "button")
            else -> tabs.narration(tab ?: tabs.selected, names)
        }
        output.add(NarratedElementType.TITLE, said)
    }

    private companion object {
        const val START = Kit.GAP
        const val END = Kit.GAP
        const val BRAND_GAP = 8
        const val MIDDLE = 2
        const val DRAG_START = 4
        const val TIP_X = 4
        const val TIP_Y = 4

        val BRAND: Component = text { brand("[ ds-mod ]") }.toComponent()
        val NEW_TAB = listOf(text { body("New Tab") }.toComponent().visualOrderText)
    }
}
