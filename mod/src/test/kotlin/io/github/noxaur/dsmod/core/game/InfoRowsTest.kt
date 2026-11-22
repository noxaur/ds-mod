package io.github.noxaur.dsmod.core.game

import kotlin.test.Test
import io.github.noxaur.dsmod.harness.text
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InfoRowsTest {
    private val tab = TabState().also { it.update(listOf("Area: The End", "Dungeon: Catacombs", "Protector: Resting").map { row -> text(row) }) }

    @Test
    fun `a feature reads the rows that tell nothing of the area`() {
        assertEquals("Resting", InfoRows(tab::info).info("Protector"))
    }

    @Test
    fun `a feature that asks for a row that tells the area is refused`() {
        val rows = InfoRows(tab::info)
        assertEquals("The End", tab.info("Area"))
        assertFailsWith<IllegalArgumentException> { rows.info("Area") }
        assertFailsWith<IllegalArgumentException> { rows.info("Dungeon") }
    }
}
