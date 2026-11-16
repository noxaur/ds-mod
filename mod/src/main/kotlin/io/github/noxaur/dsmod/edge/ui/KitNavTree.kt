package io.github.noxaur.dsmod.edge.ui

import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.NavRow
import io.github.noxaur.dsmod.core.ui.NavTree
import io.github.noxaur.dsmod.core.ui.Scroll
import io.github.noxaur.dsmod.edge.Keys
import io.github.noxaur.dsmod.edge.toComponent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.util.ARGB

/**
 * A navigation tree on a [Kit.side]: sections with a colored placeholder icon and their [names], each with the
 * named subsections in [subNames], as the navigation tree section of `controls.md` says. [tree] holds its rules.
 *
 * A press on a section runs [onSelect], and on a subsection [onJump]. A Ctrl or middle press on either runs [onOpen]
 * with the subsection, or -1 for a section, so it opens in a tab of its own. A section's arrow shows or hides its
 * subsections, and [onFold] hears that it did. While [counts] is set, as during a search, the arrows and
 * subsections are hidden and each section shows its count, dimmed at none. The wheel and the scrollbar's thumb
 * scroll it when it overflows. The screen hands it the keys with [key], since a focused search field must not
 * keep Up and Down from it, and says through [hasKeys] whether it has them, so narration speaks its cursor only then.
 */
class KitNavTree(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val tree: NavTree,
    private val names: List<String>,
    private val colors: List<Int>,
    private val subNames: List<List<String>>,
    private val onSelect: (Int) -> Unit,
    private val onJump: (Int, Int) -> Unit,
    private val onFold: () -> Unit,
    private val onOpen: (Int, Int) -> Unit,
    /** An icon in place of a section's swatch, such as Favorites' star, by section. */
    private val icons: List<Icon?> = emptyList(),
) : AbstractWidget(x, y, width, height, text { body("Sections") }.toComponent()) {
    private val scroll = Scroll()
    private val fullNames = names.map { text { body(it) }.toComponent() }
    private var tops = IntArray(1)
    private var labels = emptyArray<String>()
    private var cut = BooleanArray(0)
    private var countLabels: Array<String>? = null
    private var counts: IntArray? = null
    private var thumbGrab = -1.0

    /** Whether the keyboard moved last, which shows the cursor. A press hides it again. */
    private var keyboard = false

    /** Whether the screen gives the sidebar the keys now. */
    var hasKeys: () -> Boolean = { true }

    /** The line narration speaks of: the one the mouse came onto or the cursor moved to last. */
    private var spoken: NavRow? = null
    private var hoverRow: NavRow? = null

    private val rowWidth get() = if (scroll.overflows) width - Kit.SCROLLBAR_WIDTH - 2 else width

    init {
        refresh()
    }

    /** Shows each section's count of hits, or the tree again when [counts] is null. */
    fun counts(counts: IntArray?) {
        this.counts = counts
        countLabels = counts?.let { c -> Array(c.size) { c[it].toString() } }
        refresh()
    }

    /** Works out the lines again after the tree changed. */
    fun refresh() {
        val rows = tree.rows
        tops = IntArray(rows.size + 1)
        for (i in rows.indices) tops[i + 1] = tops[i] + if (rows[i].isSection) SECTION else SUB
        scroll.view = height
        scroll.content = tops[rows.size]
        val width = rowWidth
        labels = Array(rows.size) { i ->
            val row = rows[i]
            if (row.isSection) {
                val right = countLabels?.let { width - PAD - Kit.font.width(it[row.section]) - Kit.GAP } ?: if (tree.expandable(row.section)) chevronX(width) - Kit.GAP else width - PAD
                fit(names[row.section], right - TEXT_X)
            } else {
                fit(subNames[row.section][row.sub], width - PAD - TEXT_X)
            }
        }
        cut = BooleanArray(rows.size) { i -> rows[i].let { labels[i] != if (it.isSection) names[it.section] else subNames[it.section][it.sub] } }
    }

    /** Scrolls the tree so [row] is in sight. */
    fun reveal(row: NavRow) {
        val i = tree.rows.indexOf(row).takeIf { it >= 0 } ?: return
        if (tops[i] < scroll.offset) scroll.to(tops[i]) else if (tops[i + 1] > scroll.offset + height) scroll.to(tops[i + 1] - height)
    }

    /** Up and Down move the cursor, Right and Left open and close, and Enter opens what the cursor is on. */
    fun key(event: KeyEvent): Boolean {
        when {
            event.isUp -> tree.move(-1)
            event.isDown -> tree.move(1)
            event.isRight -> tree.right()
            event.isLeft -> tree.left()
            event.isConfirmation -> activate(tree.cursor)
            else -> return false
        }
        keyboard = true
        spoken = tree.cursor
        refresh()
        reveal(tree.cursor)
        onFold()
        return true
    }

    private fun activate(row: NavRow) = if (row.isSection) onSelect(row.section) else onJump(row.section, row.sub)

    private fun rowAt(mouseY: Double): Int {
        val at = mouseY - y + scroll.offset
        if (mouseY < y || mouseY >= y + height || at >= tops.last()) return -1
        var i = 0
        while (tops[i + 1] <= at) i++
        return i
    }

    private fun onChevron(row: NavRow, mouseX: Double) =
        row.isSection && counts == null && tree.expandable(row.section) && mouseX >= x + chevronX(rowWidth) && mouseX < x + chevronX(rowWidth) + ICON

    /** The arrow sits in a square as wide as the swatch, as far in from the row's right as the swatch is from its left. */
    private fun chevronX(rowWidth: Int) = rowWidth - PAD - ICON

    private fun onScrollbar(mouseX: Double) = scroll.overflows && mouseX >= x + width - Kit.SCROLLBAR_WIDTH

    override fun isValidClickButton(button: MouseButtonInfo) = button.button() == 0 || button.button() == MIDDLE

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        keyboard = false
        if (event.button() == MIDDLE || Keys.control(event)) {
            val i = rowAt(event.y).takeIf { it >= 0 && !onScrollbar(event.x) } ?: return
            val row = tree.rows[i]
            return onOpen(row.section, row.sub)
        }
        if (onScrollbar(event.x)) {
            val thumbY = y + 1 + scroll.thumbY(height - 2)
            val thumb = scroll.thumb(height - 2)
            thumbGrab = if (event.y >= thumbY && event.y < thumbY + thumb) event.y - thumbY else thumb / 2.0
            scroll.drag(event.y - y - 1 - thumbGrab, height - 2)
            return
        }
        val i = rowAt(event.y).takeIf { it >= 0 } ?: return
        val row = tree.rows[i]
        tree.moveTo(row)
        spoken = row
        if (onChevron(row, event.x)) {
            tree.toggle(row.section)
            refresh()
            onFold()
        } else {
            activate(row)
        }
    }

    override fun onDrag(event: MouseButtonEvent, dx: Double, dy: Double) {
        if (thumbGrab >= 0) scroll.drag(event.y - y - 1 - thumbGrab, height - 2)
    }

    override fun onRelease(event: MouseButtonEvent) {
        thumbGrab = -1.0
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!scroll.overflows || scrollY == 0.0) return false
        scroll.by(if (scrollY > 0) -WHEEL else WHEEL)
        return true
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val t = Kit.theme
        val rows = tree.rows
        val width = rowWidth
        val counts = counts
        val over = if (isHovered && !onScrollbar(mouseX.toDouble())) rowAt(mouseY.toDouble()) else -1
        val hover = rows.getOrNull(over)
        if (hover != hoverRow) {
            hoverRow = hover
            if (hover != null) spoken = hover
        }
        val offset = scroll.offset
        graphics.enableScissor(x, y, x + this.width, y + height)
        for (i in rows.indices) {
            val top = y + tops[i] - offset
            if (top >= y + height || top + SECTION <= y) continue
            val row = rows[i]
            val lit = i == over || keyboard && row == tree.cursor
            if (row.isSection) {
                val dim = counts != null && counts[row.section] == 0
                Kit.navRow(graphics, x, top, width, SECTION, if (lit) Kit.State.HOVER else Kit.State.IDLE, tree.marked(row))
                val color = if (dim) ARGB.color(DIM_ALPHA, colors[row.section]) else colors[row.section]
                val icon = icons.getOrNull(row.section)
                if (icon != null) {
                    Kit.icon(graphics, icon, x + PAD + (ICON - icon.width) / 2, top + (SECTION - icon.height) / 2, color)
                } else {
                    Kit.swatch(graphics, x + PAD, top + (SECTION - ICON) / 2, ICON, color)
                }
                Kit.label(graphics, labels[i], x + TEXT_X, top + 4, if (dim) Kit.Ink.OFF else Kit.Ink.NORMAL)
                val countLabels = countLabels
                if (countLabels != null) {
                    val label = countLabels[row.section]
                    // The text's width counts the blank column after its last letter, which the right edge's padding already holds.
                    if (!dim) Kit.label(graphics, label, x + width - PAD - Kit.font.width(label) + 1, top + 4, Kit.Ink.MUTED)
                } else if (tree.expandable(row.section)) {
                    val cx = x + chevronX(width)
                    val cy = top + (SECTION - ICON) / 2
                    val icon = if (tree.shows(row.section)) Icon.DOWN else Icon.RIGHT
                    if (i == over && onChevron(row, mouseX.toDouble())) {
                        Kit.button(graphics, cx, cy, ICON, ICON, Kit.State.HOVER, icon)
                    } else {
                        Kit.icon(graphics, icon, cx + (ICON - icon.width) / 2, cy + (ICON - icon.height) / 2, t.inkMuted, t.inkShadow)
                    }
                }
                if (i == over && cut[i] && !onChevron(row, mouseX.toDouble())) graphics.setTooltipForNextFrame(Kit.font, fullNames[row.section], mouseX, mouseY)
            } else {
                if (lit) Kit.navRow(graphics, x, top, width, SUB, Kit.State.HOVER, false)
                Kit.label(graphics, labels[i], x + TEXT_X, top + 3, if (lit || tree.marked(row)) Kit.Ink.NORMAL else Kit.Ink.MUTED)
            }
        }
        graphics.disableScissor()
        if (scroll.overflows) Kit.scrollbar(graphics, x + this.width - Kit.SCROLLBAR_WIDTH, y, height, scroll.thumbY(height - 2), scroll.thumb(height - 2))
    }

    override fun narrationPriority(): NarratableEntry.NarrationPriority = when {
        hasKeys() && (keyboard || isFocused) -> NarratableEntry.NarrationPriority.FOCUSED
        hoverRow != null -> NarratableEntry.NarrationPriority.HOVERED
        else -> NarratableEntry.NarrationPriority.NONE
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        val rows = tree.rows
        val row = spoken?.takeIf { it in rows } ?: tree.cursor
        output.add(NarratedElementType.TITLE, tree.narration(row, names, subNames, counts))
        if (rows.size > 1) output.add(NarratedElementType.POSITION, I18n.get("narrator.position.list", rows.indexOf(row) + 1, rows.size))
    }

    companion object {
        const val SECTION = 16
        const val SUB = 13
        const val ICON = 9
        const val PAD = 5

        /** Where a section's name and its subsections' names start, after the icon. */
        const val TEXT_X = PAD + ICON + Kit.GAP
        private const val WHEEL = SECTION
        private const val DIM_ALPHA = 96
        private const val MIDDLE = 2
    }
}
