package io.github.noxaur.dsmod.core.profile

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reads what the backend answers, from `profile.json` and `inventory.json`, which the backend's own tests produced
 * from their synthetic player, so the mod's reading follows the contract and not a copy of it.
 */
class ProfileTest {
    private fun resource(name: String) = checkNotNull(javaClass.getResource("/profiles/$name")).readText()

    private val profile = Profile.parse(resource("profile.json"))

    @Test
    fun `a profile reads whole`() {
        assertEquals("Testwyrm", profile.player.name)
        assertEquals("MVP_PLUS", profile.player.rank.id)
        assertEquals("[MVP+]", profile.player.rank.prefix.plain)
        assertEquals(listOf("Arcane", "Superior"), profile.profiles.map { it.name.plain })
        assertEquals(0xAA00AA, profile.profiles[0].name.spans.single().color)
        assertEquals("Superior", profile.choice?.name?.plain)
        assertEquals(listOf("combat", "mining", "taming", "catacombs", "dragon_slaying"), profile.skills.map { it.id })
        assertEquals("Revenant Horror", profile.slayers[0].name)
        assertEquals(ServerText(ServerSpan("35,124,030,742", 0xFFAA00)), profile.stats.first { it.id == "coins" }.value)
        assertEquals(10, profile.topKills.size)
        assertEquals("Storm Helmet", profile.equipment.helmet?.name?.plain)
        assertEquals("minecraft:iron_sword", profile.equipment.held?.material)
        assertFalse(profile.hidden)
    }

    @Test
    fun `dungeons read with the picked class and the floors in order`() {
        val dungeons = profile.dungeons
        assertEquals("Tank", dungeons.selectedClass)
        assertEquals("tank", dungeons.selected?.id)
        assertEquals(listOf("Healer", "Mage", "Berserk", "Archer", "Tank"), dungeons.classes.map { it.name })
        assertEquals(listOf("F1", "F2", "F7", "M1"), dungeons.floors.map { it.id })
        assertEquals(listOf(false, false, false, true), dungeons.floors.map { it.master })
        assertNull(dungeons.floors.last().bestScore)
        assertEquals(486, dungeons.completions)
    }

    @Test
    fun `a backend that sends no dungeons reads as none`() {
        val body = JsonObject(Json.parseToJsonElement(resource("profile.json")).jsonObject - "dungeons")
        assertEquals(Dungeons(), Profile.parse(body.toString()).dungeons)
    }

    @Test
    fun `one tab holds every page of a kind`() {
        assertEquals(listOf("inventory", "ender_chest", "storage", "storage", "storage", "wardrobe", "accessories"), profile.containers.map { it.kind })
    }

    @Test
    fun `a container has an entry for every slot, null where it is empty`() {
        val inventory = Profile.parseContainer(resource("containers/inventory.json"))
        assertEquals(36, inventory.items.size)
        assertEquals(inventory.slots, inventory.items.size)
        assertEquals(6, inventory.items.count { it != null })
        val scylla = inventory.items.filterNotNull().first { it.entry == "scylla" }
        assertTrue(scylla.lore.isNotEmpty())
    }

    @Test
    fun `a field the mod does not know is passed over`() {
        val body = resource("profile.json").replaceFirst("\"stale\"", "\"newField\": [1, 2], \"stale\"")
        assertEquals(profile, Profile.parse(body))
    }

    @Test
    fun `a bar is full at the cap and empty when the next level's size is unknown`() {
        fun progress(level: Int, into: Double, next: Double?) = Progress("x", "X", level, 9, 100.0, into, next)
        assertEquals(0.25f, progress(1, 50.0, 200.0).fraction)
        assertEquals(1f, progress(9, 0.0, null).fraction)
        assertEquals(0f, progress(3, 40.0, null).fraction)
        assertEquals(1f, progress(3, 500.0, 200.0).fraction)
        assertNull(profile.slayers[0].xpForNextLevel)
    }

    @Test
    fun `a vault number belongs to the server's pv, and anything else is a name`() {
        assertTrue(Profile.vault("1"))
        assertTrue(Profile.vault("12"))
        assertFalse(Profile.vault("123"))
        assertFalse(Profile.vault("Steve"))
        assertFalse(Profile.vault("1a"))
        assertTrue(Profile.validName("jeb_"))
        assertFalse(Profile.validName("no spaces"))
        assertFalse(Profile.validName("a".repeat(17)))
    }
}
