package io.github.noxaur.dsmod.core.profile

import io.github.noxaur.dsmod.harness.resource
import io.github.noxaur.dsmod.harness.text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val profile = Profile.parse(resource("profiles/profile.json"))

    @Test
    fun `a profile reads whole`() {
        assertEquals("Testwyrm", profile.player.name)
        assertEquals("MVP_PLUS", profile.player.rank.id)
        assertEquals("[MVP+]", profile.player.rank.prefix.plain)
        assertEquals(listOf("Arcane", "Superior"), profile.profiles.map { it.name.plain })
        assertEquals(0xAA00AA, profile.profiles[0].name.spans.single().color)
        assertEquals("Superior", profile.choice.name.plain)
        assertEquals(listOf("combat", "mining", "taming", "catacombs", "dragon_slaying"), profile.skills.map { it.id })
        assertEquals("Revenant Horror", profile.slayers[0].name)
        assertEquals(text("35,124,030,742", 0xFFAA00), profile.stats.first { it.id == "coins" }.value)
        assertEquals(10, profile.topKills.size)
        assertEquals("Storm Helmet", profile.equipment.helmet?.name?.plain)
        assertEquals("minecraft:iron_sword", profile.equipment.held?.material)
        assertFalse(profile.hidden)
    }

    @Test
    fun `dungeons read with the picked class, and every class listed in the game's order`() {
        val dungeons = checkNotNull(profile.dungeons)
        assertEquals(Named("TANK", "Tank"), dungeons.selectedClass)
        assertEquals(listOf("MAGE", "TANK"), dungeons.classes.map { it.id })
        // The server sends only the classes with XP, so the rest stand at level 0 in their places.
        assertEquals(listOf("HEALER", "MAGE", "BERSERK", "ARCHER", "TANK"), dungeons.allClasses.map { it.id })
        assertEquals(listOf(0, 14, 0, 0, 18), dungeons.allClasses.map { it.level })
        assertEquals(listOf("TANK"), dungeons.allClasses.filter(dungeons::picked).map { it.id })
        assertEquals(listOf(false, true), dungeons.floors.map { it.master })
        assertNull(dungeons.floors.last().bestScore)
        assertEquals(469, dungeons.completions)
        assertEquals(listOf("Wither Essence"), dungeons.essence.map { it.name })
    }

    @Test
    fun `a backend that sends no dungeons reads as none`() {
        val body = JsonObject(Json.parseToJsonElement(resource("profiles/profile.json")).jsonObject - "dungeons")
        assertNull(Profile.parse(body.toString()).dungeons)
    }

    @Test
    fun `everything else the player has reads too`() {
        assertEquals(2, profile.equippedWardrobeSlot)
        assertEquals(0x55FFFF, profile.playerStats.base.first { it.id == "intelligence" }.rgb)
        assertNull(StatValue("x", "X", "not a color", 1.0).rgb)
        assertEquals(profile.bestiary.take(10).map { it.id }, profile.topKills.map { it.id })
        assertEquals("Void Infusion", profile.hotd?.ability?.name)
        val data = profile.equipment.helmet?.customData?.get("item_data")?.jsonObject
        assertEquals("maxor_helmet", data?.get("item_id")?.jsonPrimitive?.content)
        val pet = profile.pets.single { it.active }
        assertEquals("Lucky Clover", pet.heldItem?.name)
        assertEquals("[Lvl 150] Golden Dragon", pet.item.name.plain)
        assertEquals(150, pet.level)
        val forge = profile.forge.first { it.stage == "RUNNING" }
        assertEquals(0f, forge.done(forge.startedAt))
        assertEquals(0.5f, forge.done(forge.startedAt + forge.durationMs / 2))
        assertEquals(1f, forge.done(forge.finishesAt + 1))
    }

    @Test
    fun `a profile the list leaves out is still picked, by its own name and mode`() {
        assertEquals(profile.profiles, profile.choices)
        val unlisted = profile.copy(profileId = "bbbbbbbb-0000-4000-8000-000000000009")
        assertEquals("Superior", unlisted.choice.name.plain)
        assertEquals("IRONMAN", unlisted.choice.mode)
        assertEquals(profile.profiles + unlisted.choice, unlisted.choices)
    }

    @Test
    fun `one tab holds every page of a kind`() {
        assertEquals(listOf("inventory", "ender_chest", "storage", "storage", "storage", "wardrobe", "accessories", "pets"), profile.containers.map { it.kind })
        assertEquals("inventory", ContainerSummary("inventory:other", text(), 9, 5, false).kind)
    }

    @Test
    fun `a container has an entry for every slot, null where it is empty`() {
        val inventory = Profile.parseContainer(resource("profiles/containers/inventory.json"))
        assertEquals(36, inventory.items.size)
        assertEquals(inventory.slots, inventory.items.size)
        assertEquals(6, inventory.items.count { it != null })
        val scylla = inventory.items.filterNotNull().first { it.entry == "scylla" }
        assertTrue(scylla.lore.isNotEmpty())
    }

    @Test
    fun `a field the mod does not know is passed over`() {
        val body = resource("profiles/profile.json").replaceFirst("\"stale\"", "\"newField\": [1, 2], \"stale\"")
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
