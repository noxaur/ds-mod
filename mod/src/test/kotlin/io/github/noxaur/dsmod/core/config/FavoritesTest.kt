package io.github.noxaur.dsmod.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoritesTest {
    private val all = listOf("general.fullbright", "end.zealotTracker", "loot.lootAlerts", "slayer.slayerTracker")

    @Test
    fun `a press stars a setting and a second press unstars it`() {
        val starred = Favorites.toggle(emptyList(), "loot.lootAlerts")
        assertEquals(listOf("loot.lootAlerts"), starred)
        assertEquals(emptyList(), Favorites.toggle(starred, "loot.lootAlerts"))
    }

    @Test
    fun `favorites keep the settings' own order, not the order they were starred in`() {
        val starred = listOf("slayer.slayerTracker", "general.fullbright", "loot.lootAlerts")
        assertEquals(listOf("general.fullbright", "loot.lootAlerts", "slayer.slayerTracker"), Favorites.pick(all, starred) { it })
    }

    @Test
    fun `a starred setting that no longer exists is passed over`() {
        assertEquals(listOf("general.fullbright"), Favorites.pick(all, listOf("general.gone", "general.fullbright")) { it })
    }

    @Test
    fun `a search hides Favorites, so each match shows once in its own category`() {
        assertTrue(Favorites.listed(""))
        assertTrue(Favorites.listed("  "))
        assertFalse(Favorites.listed("zealot"))
    }

    @Test
    fun `nothing starts starred`() {
        assertEquals(emptyList(), DsModConfig().favorites)
    }

    @Test
    fun `a row unstarred in Favorites stays until the player leaves`() {
        val view = FavoritesView()
        val starred = listOf("general.fullbright", "loot.lootAlerts")
        val after = view.toggle(starred, "general.fullbright", here = true)
        assertEquals(listOf("loot.lootAlerts"), after)
        // The row stays in place, so a quick second press lands on it and stars it again.
        assertEquals(listOf("general.fullbright", "loot.lootAlerts"), view.rows(all, after) { it })
        val again = view.toggle(after, "general.fullbright", here = true)
        assertEquals(listOf("loot.lootAlerts", "general.fullbright"), again)
        assertEquals(listOf("general.fullbright", "loot.lootAlerts"), view.rows(all, again) { it })

        view.toggle(again, "loot.lootAlerts", here = true).let { left ->
            view.left()
            assertEquals(listOf("general.fullbright"), view.rows(all, left) { it })
        }
    }

    @Test
    fun `a setting unstarred in its own category leaves Favorites at once`() {
        val view = FavoritesView()
        val after = view.toggle(listOf("general.fullbright", "loot.lootAlerts"), "general.fullbright", here = false)
        assertEquals(listOf("loot.lootAlerts"), view.rows(all, after) { it })
    }
}
