package io.github.noxaur.dsmod.core.config

import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `every default favorite is a setting the screen shows`() {
        for (path in Favorites.DEFAULT) {
            val (category, option) = path.split('.')
            val holder = DsModConfig::class.java.getField(category).type
            assertNotNull(holder.getField(option).getAnnotation(ConfigOption::class.java), "$path is not a setting")
        }
    }

    @Test
    fun `a new player starts with the defaults starred`() {
        assertEquals(Favorites.DEFAULT, DsModConfig().favorites)
    }
}
