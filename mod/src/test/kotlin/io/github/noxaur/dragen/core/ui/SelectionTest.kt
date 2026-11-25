package io.github.noxaur.dragen.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionTest {
    private val all = listOf("a", "b", "c", "d", "e", "f")
    private val none = Selection<String>()

    private fun Selection<String>.click(item: String, shift: Boolean = false, ctrl: Boolean = false) = press(all, item, shift, ctrl).over(all, item)

    @Test
    fun `a press selects one item and makes it the anchor`() {
        val one = none.click("c")
        assertEquals(setOf("c"), one.items)
        assertEquals("c", one.anchor)
        assertEquals(setOf("e"), one.click("e").items)
    }

    @Test
    fun `a drag selects the range either way and backs off`() {
        val drag = none.press(all, "c", extend = false, toggle = false)
        assertEquals(setOf("c", "d", "e"), drag.over(all, "e").items)
        assertEquals(setOf("a", "b", "c"), drag.over(all, "a").items)
        assertEquals(setOf("c"), drag.over(all, "c").items)
    }

    @Test
    fun `shift extends from the anchor and keeps it`() {
        val range = none.click("b").click("e", shift = true)
        assertEquals(setOf("b", "c", "d", "e"), range.items)
        assertEquals("b", range.anchor)
        assertEquals(setOf("a", "b"), range.click("a", shift = true).items)
    }

    @Test
    fun `shift still finds the anchor after the selection was cleared`() =
        assertEquals(setOf("b", "c", "d"), none.click("b").cleared().click("d", shift = true).items)

    @Test
    fun `shift with no anchor or one that left the list selects the pressed item`() {
        assertEquals(setOf("d"), none.click("d", shift = true).items)
        assertEquals(setOf("d"), Selection(setOf("x"), "x", "x").click("d", shift = true).items)
    }

    @Test
    fun `ctrl adds an item, takes one out and moves the anchor`() {
        val two = none.click("a").click("d", ctrl = true)
        assertEquals(setOf("a", "d"), two.items)
        assertEquals("d", two.anchor)
        assertEquals(setOf("d"), two.click("a", ctrl = true).items)
    }

    @Test
    fun `ctrl and a drag adds a range, or takes one out when it starts on a selected item`() {
        val two = none.click("a").press(all, "c", extend = false, toggle = true).over(all, "d")
        assertEquals(setOf("a", "c", "d"), two.items)
        assertEquals(setOf("a"), two.press(all, "c", extend = false, toggle = true).over(all, "e").items)
    }

    @Test
    fun `ctrl and shift add the range from the anchor`() =
        assertEquals(setOf("a", "c", "d", "e"), none.click("a").click("c", ctrl = true).click("e", shift = true, ctrl = true).items)

    @Test
    fun `a press inside a selection of several keeps it, and a drag out of the item starts over`() {
        val three = none.click("b").click("d", shift = true)
        val drag = three.press(all, "c", extend = false, toggle = false)
        assertEquals(three, drag.over(all, "c"))
        assertEquals(setOf("c", "d", "e"), drag.over(all, "e").items)
    }

    @Test
    fun `select all takes every item`() = assertEquals(all.toSet(), none.click("c").all(all).items)

    @Test
    fun `a step moves the focus from the anchor and stops at the ends`() {
        val up = none.click("c").step(all, -1)
        assertEquals(setOf("b", "c"), up.items)
        assertEquals(setOf("c", "d"), up.step(all, 2).items)
        assertEquals(setOf("a", "b", "c"), up.step(all, -5).items)
        assertEquals(none, none.step(all, 1))
    }

    @Test
    fun `ordered follows the list, and items that left it are skipped`() =
        assertEquals(listOf("b", "e"), Selection(setOf("e", "x", "b")).ordered(all))
}
