package io.github.noxaur.dragen.core.config

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoritesTest {
    private val all = listOf("general.fullbright", "end.zealotTracker", "loot.rareDropAlert", "slayer.slayerTracker")

    @Test
    fun `a press stars a setting and a second press unstars it`() {
        val starred = Favorites.toggle(emptyList(), "loot.rareDropAlert")
        assertEquals(listOf("loot.rareDropAlert"), starred)
        assertEquals(emptyList(), Favorites.toggle(starred, "loot.rareDropAlert"))
    }

    @Test
    fun `favorites keep the settings' own order, not the order they were starred in`() {
        val starred = listOf("slayer.slayerTracker", "general.fullbright", "loot.rareDropAlert")
        assertEquals(listOf("general.fullbright", "loot.rareDropAlert", "slayer.slayerTracker"), Favorites.pick(all, starred) { it })
    }

    @Test
    fun `a starred setting that no longer exists is passed over`() {
        assertEquals(listOf("general.fullbright"), Favorites.pick(all, listOf("general.gone", "general.fullbright")) { it })
    }

    @Test
    fun `nothing starts starred`() {
        assertEquals(emptyList(), DragenConfig().favorites)
    }

    @Test
    fun `a row unstarred in Favorites stays until the player leaves`() {
        val view = FavoritesView()
        val starred = listOf("general.fullbright", "loot.rareDropAlert")
        val after = view.toggle(starred, "general.fullbright", here = true)
        assertEquals(listOf("loot.rareDropAlert"), after)
        assertEquals(listOf("general.fullbright", "loot.rareDropAlert"), view.rows(all, after) { it })
        val again = view.toggle(after, "general.fullbright", here = true)
        assertEquals(listOf("loot.rareDropAlert", "general.fullbright"), again)
        assertEquals(listOf("general.fullbright", "loot.rareDropAlert"), view.rows(all, again) { it })

        view.toggle(again, "loot.rareDropAlert", here = true).let { left ->
            view.left()
            assertEquals(listOf("general.fullbright"), view.rows(all, left) { it })
        }
    }

    @Test
    fun `Favorites lists each starred row under its group, a heading's switch as a row, and not a row under another`() {
        val tree = SettingsTree().sections
        val hits = Favorites.hits(tree, listOf("slotLockKey", "etherwarp-outline", "etherwarpWidth", "slot-lock", "rare-drop-alert", "gone"))
        assertEquals(
            listOf("Protection" to listOf("slot-lock"), "Items" to listOf("rare-drop-alert"), "Etherwarp" to listOf("etherwarp-outline", "etherwarpWidth")),
            hits.map { it.group.name to it.rows.map(Setting::id) },
        )
        assertEquals(listOf("Inventory", "Inventory", "Visuals"), hits.map { tree[it.section].name })
    }

    @Test
    fun `a favorite saved by MoulConfig's path becomes its row's id`() {
        assertEquals(
            listOf("fullbright", "hudScale", "binds", "moveHud", "beamSections", "theme"),
            SettingsTree.favorites(listOf("general.fullbright", "hud.scale", "general.editBinds", "hud.edit", "end.editBeam", "theme", "general.gone", "hud.scale")),
        )
    }

    @Test
    fun `a feature's switch starred under its key, or a former key, becomes the feature's id`() {
        assertEquals(
            listOf("rare-drop-alert", "protector-spawn-beam", "hide-cooldown-messages", "fullbright"),
            SettingsTree.favorites(listOf("rareDropAlert", "bossBeacon", "chat.abilitySpam", "fullbright")),
        )
    }

    @Test
    fun `a feature that became another's part keeps its star and its rows' stars`() {
        assertEquals(listOf("autoClickLeft", "autoClickRightKey"), SettingsTree.favorites(listOf("auto-left-click", "autoRightClickKey")))
    }

    @Test
    fun `a setting unstarred in its own category leaves Favorites at once`() {
        val view = FavoritesView()
        val after = view.toggle(listOf("general.fullbright", "loot.rareDropAlert"), "general.fullbright", here = false)
        assertEquals(listOf("loot.rareDropAlert"), view.rows(all, after) { it })
    }
}
