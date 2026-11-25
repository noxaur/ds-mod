package io.github.noxaur.dragen.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemSearchTest {
    private val lore = listOf("Damage: +250", "LEGENDARY SWORD")

    @Test
    fun `a query matches the name or the lore, ignoring case`() {
        assertTrue(ItemSearch.matches(ItemSearch.words("dragon"), "Dragon Blade", lore))
        assertTrue(ItemSearch.matches(ItemSearch.words("legendary"), "Dragon Blade", lore))
        assertFalse(ItemSearch.matches(ItemSearch.words("scale"), "Dragon Blade", lore))
    }

    @Test
    fun `every word must appear, in any order and any line`() {
        assertTrue(ItemSearch.matches(ItemSearch.words("sword  blade"), "Dragon Blade", lore))
        assertFalse(ItemSearch.matches(ItemSearch.words("sword scale"), "Dragon Blade", lore))
    }

    @Test
    fun `a blank query has no words and matches everything`() {
        assertEquals(emptyList(), ItemSearch.words("   "))
        assertTrue(ItemSearch.matches(ItemSearch.words(""), "Dirt", emptyList()))
    }
}
