package io.github.noxaur.dsmod.core.ui

import kotlin.math.ceil
import kotlin.math.sqrt

/** @keep A cell is a slot's 18 square, with its item 1 in. */
class StorageLayout(screenWidth: Int, screenHeight: Int, private val rows: List<Int>) {
    val columns: Int
    val pagesWidth: Int
    val content: Int
    val view: Int
    val left: Int
    val top: Int
    val width: Int
    val height: Int
    val viewX: Int
    val viewY: Int

    private val pageX = IntArray(rows.size)
    private val pageY = IntArray(rows.size)

    init {
        val fit = maxOf(1, minOf(rows.size, (screenWidth - 2 * MARGIN_X - 2 * INSET - SCROLL_ROOM + COLUMN_GAP) / (GRID + COLUMN_GAP)))
        columns = minOf(fit, maxOf(1, ceil(sqrt(rows.size.toDouble())).toInt()))
        content = place(columns)
        val bottom = minOf(screenHeight, (screenHeight + Menus.TALLEST) / 2)
        val room = maxOf(bottom - MARGIN_Y - 2 * INSET - INVENTORY_GAP - INVENTORY, MIN_VIEW)
        pagesWidth = columns * GRID + (columns - 1) * COLUMN_GAP
        view = minOf(content, room)
        width = 2 * INSET + pagesWidth + if (overflows) SCROLL_ROOM else 0
        height = 2 * INSET + view + INVENTORY_GAP + INVENTORY
        left = (screenWidth - width) / 2
        top = maxOf(0, bottom - height)
        viewX = left + INSET
        viewY = top + INSET
        for (i in pageX.indices) pageX[i] += viewX
    }

    private fun place(columns: Int): Int {
        var y = 0
        for (start in rows.indices step columns) {
            val end = minOf(start + columns, rows.size)
            for (i in start until end) {
                pageX[i] = (i - start) * (GRID + COLUMN_GAP)
                pageY[i] = y
            }
            y += TITLE + (start until end).maxOf { rows[it] } * CELL + ROW_GAP
        }
        return if (rows.isEmpty()) 0 else y - ROW_GAP
    }

    val overflows get() = content > view

    val pages get() = rows.size

    fun rows(page: Int) = rows[page]

    fun pageLeft(page: Int) = pageX[page]

    fun titleTop(page: Int, offset: Int) = viewY + pageY[page] - offset

    fun gridTop(page: Int, offset: Int) = titleTop(page, offset) + TITLE

    fun shows(page: Int, offset: Int) = titleTop(page, offset) >= viewY && gridTop(page, offset) + rows[page] * CELL <= viewY + view

    fun touches(page: Int, offset: Int) = gridTop(page, offset) + rows[page] * CELL > viewY && titleTop(page, offset) < viewY + view

    fun cellShows(page: Int, cell: Int, offset: Int): Boolean {
        val y = gridTop(page, offset) + cell / ROW * CELL
        return y >= viewY && y + CELL <= viewY + view
    }

    fun cellX(page: Int, cell: Int) = pageX[page] + cell % ROW * CELL

    fun cellY(page: Int, cell: Int, offset: Int) = gridTop(page, offset) + cell / ROW * CELL

    fun inView(x: Double, y: Double) = x >= viewX && x < viewX + pagesWidth && y >= viewY && y < viewY + view

    fun pageAt(x: Double, y: Double, offset: Int): Int {
        if (!inView(x, y)) return -1
        for (i in rows.indices) {
            if (x >= pageX[i] && x < pageX[i] + GRID && y >= titleTop(i, offset) && y < gridTop(i, offset) + rows[i] * CELL) return i
        }
        return -1
    }

    fun cellAt(page: Int, x: Double, y: Double, offset: Int): Int {
        val dx = x - pageX[page]
        val dy = y - gridTop(page, offset)
        if (dx < 0 || dy < 0 || dx >= GRID || dy >= rows[page] * CELL || !inView(x, y)) return -1
        return dy.toInt() / CELL * ROW + dx.toInt() / CELL
    }

    val scrollbarX get() = viewX + pagesWidth + SCROLL_GAP

    val pagesHeight get() = 2 * INSET + view

    val inventoryX get() = viewX + (pagesWidth - GRID) / 2

    val stemX get() = inventoryX - INSET

    val inventoryTitleY get() = viewY + view + INVENTORY_GAP

    fun inventoryCellY(slot: Int) = inventoryTitleY + TITLE + slot / ROW * CELL + if (slot >= HOTBAR) HOTBAR_GAP else 0

    fun inventoryCellX(slot: Int) = inventoryX + slot % ROW * CELL

    companion object {
        const val CELL = 18
        const val ROW = 9
        const val GRID = ROW * CELL
        const val TITLE = 11
        const val INSET = 7
        const val COLUMN_GAP = 8
        const val ROW_GAP = 6
        const val SCROLL_GAP = 4
        const val SCROLLBAR = 6
        const val SCROLL_ROOM = SCROLL_GAP + SCROLLBAR
        const val MARGIN_X = 8
        const val MARGIN_Y = 22
        const val INVENTORY_GAP = 6
        const val HOTBAR = 27
        const val HOTBAR_GAP = 4
        const val INVENTORY = TITLE + 4 * CELL + HOTBAR_GAP
        const val STEM_WIDTH = GRID + 2 * INSET
        const val MIN_VIEW = TITLE + 3 * CELL
    }
}
