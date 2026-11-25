package io.github.noxaur.dragen.core.hud

import io.github.noxaur.dragen.core.text.Role
import kotlin.test.Test
import kotlin.test.assertEquals

class HudBlockTest {
    @Test
    fun `a row is a muted label followed by its content`() {
        val block = hud("Scale Tracker") {
            row("Sold") { value(1204) }
        }

        assertEquals("Scale Tracker", block.header.plain)
        assertEquals("Sold: 1,204", block.rows.single().text.plain)
        assertEquals(listOf(Role.MUTED.rgb, Role.VALUE.rgb), block.rows.single().text.spans.map { it.rgb })
    }

    @Test
    fun `a bar keeps its fraction between empty and full`() {
        val block = hud("Storage") {
            bar("Used", 1.4f)
            bar("Free", -0.2f)
            bar("Half", 0.5f, Role.WARNING)
        }

        assertEquals(listOf(1f, 0f, 0.5f), block.rows.map { (it as BarRow).fraction })
        assertEquals(Role.WARNING, (block.rows.last() as BarRow).role)
    }
}
