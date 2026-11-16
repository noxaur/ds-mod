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

/**
 * Browser tabs across the top of a pane, as the tab bar section of `controls.md` says: one per tab of [tabs], each
 * with its section's color from [colors] or the search icon and its title from [names], then the New Tab button, and
 * `[ ds-mod ]` at the right end. [tabs] and [TabStrip] hold the rules, and the screen makes each change through the
 * callbacks: a press selects a tab, a press on its × or a middle press closes it, a press on the plus opens a new
 * one, and a sideways drag moves it. Call [layout] after the tabs change.
 */
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
    /** An icon in place of a section's swatch, such as Favorites' star, by section. */
    private val icons: List<Icon?> = emptyList(),
) : AbstractWidget(x, y, width, Kit.TAB_BAR_HEIGHT, text { body("Tabs") }.toComponent()) {
    private val strip = TabStrip()
    private val brandWidth = Kit.font.width(BRAND)
    private var labels = emptyArray<String>()

    /** Each tab's whole title as a tooltip, where its label was cut short. */
    private var cut = emptyArray<List<FormattedCharSequence>?>()

    // A tooltip above the mouse would leave the window here, so the bar's open below it, at the mouse.
    private val below = ClientTooltipPositioner { screenWidth, _, mouseX, _, tipWidth, _ ->
        Vector2i((mouseX + TIP_X).coerceAtMost(screenWidth - tipWidth - TIP_X), y + height + TIP_Y)
    }

    /** The tab being dragged, and where the press was. The drag starts once the mouse has moved a few pixels. */
    private var dragged = -1
    private var origin = -1
    private var grab = 0
    private var pressX = 0.0
    private var dragX = 0
    private var live = false

    /** What is under the mouse, kept from the last frame for narration: a tab, its ×, or the New Tab button. */
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
        // Too narrow for a letter, a tab shows its icon alone, as a browser's does, rather than a lone ellipsis.
        labels = Array(tabs.size) { i -> fit(tabs.tabs[i].title(names), Kit.pageTabLabel(strip.width, lead(i), close)).takeUnless { it == "..." && lead(i) } ?: "" }
        cut = Array(tabs.size) { i -> tabs.tabs[i].title(names).let { if (it == labels[i]) null else listOf(text { body(it) }.toComponent().visualOrderText) } }
    }

    /** Puts a dragged tab back where the drag started. Returns whether a drag was on. */
    fun cancelDrag(): Boolean {
        if (!live) return false
        if (dragged != origin) onMove(dragged, origin)
        dragged = -1
        live = false
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
        if (onNew(event.x)) {
            if (event.button() == 0) onNew()
            return
        }
        val i = tabAt(event.x).takeIf { it >= 0 } ?: return
        if (event.button() == MIDDLE || onClose(i, event.x, event.y)) return onClose(i)
        onSelect(i)
        dragged = i
        origin = i
        grab = event.x.toInt() - stripX - strip.x(i)
        pressX = event.x
        live = false
    }

    override fun onDrag(event: MouseButtonEvent, dx: Double, dy: Double) {
        if (dragged < 0 || !live && abs(event.x - pressX) < DRAG_START) return
        live = true
        dragX = event.x.toInt() - stripX
        val to = strip.drop(dragX, dragged)
        if (to == dragged) return
        onMove(dragged, to)
        dragged = to
    }

    override fun onRelease(event: MouseButtonEvent) {
        dragged = -1
        live = false
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
        val over = if (isHovered && !live) tabAt(mx) else -1
        val all = tabs.tabs
        graphics.enableScissor(stripX, y, stripX + strip.view, y + height)
        for (i in all.indices) {
            if (live && i == dragged) continue
            tab(graphics, i, stripX + strip.x(i), if (i == over) Kit.State.HOVER else Kit.State.IDLE, i == over && onClose(i, mx, my))
        }
        if (live) tab(graphics, dragged, stripX + strip.dragX(dragX, grab), Kit.State.DOWN, false)
        graphics.disableScissor()
        val overNew = isHovered && !live && onNew(mx)
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

    /** The tab, × or plus under the mouse, or else the selected tab. */
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
        // The tabs start as far in from the bar's left as the brand ends from its right.
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
