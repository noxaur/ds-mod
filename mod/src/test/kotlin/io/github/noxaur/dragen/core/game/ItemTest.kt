package io.github.noxaur.dragen.core.game

import io.github.noxaur.dragen.harness.lines
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemTest {
    private fun item(vararg lore: String, data: String = "{}") =
        Item("minecraft:paper", 1, lore = lines(*lore), data = Json.parseToJsonElement(data).jsonObject)

    @Test
    fun `clean lore drops the resource pack's glyphs and trims each line`() {
        assertEquals(listOf("Giant's Slam RIGHT CLICK", "Held Item", ""), item(" Giant's Slam RIGHT CLICK ", "Held Item", "").cleanLore)
    }

    @Test
    fun `a section is the lines under its header up to the next blank line, and empty without the header`() {
        val forge = item("Items Required", "Enchanted End Stone x16", "Diamond", "", "Duration: 24m 36s")
        assertEquals(listOf("Enchanted End Stone x16", "Diamond"), forge.section("Items Required"))
        assertEquals(listOf("Diamond"), item("Items Required", "Diamond").section("Items Required"))
        assertEquals(emptyList(), forge.section("Held Item"))
    }

    @Test
    fun `sections under a pattern end at a blank line or the next heading`() {
        val found = mutableListOf<Pair<String, List<String>>>()
        item("Intro", "A RIGHT CLICK", "Cooldown: 1s", "B LEFT CLICK", "Cooldown: 2s", "", "Mana", "C RIGHT CLICK")
            .sections(Regex("(.+) (?:RIGHT|LEFT) CLICK")) { heading, lines -> found += heading.groupValues[1] to lines }
        assertEquals(listOf("A" to listOf("Cooldown: 1s"), "B" to listOf("Cooldown: 2s"), "C" to emptyList()), found)
    }

    @Test
    fun `a data path reads a string, and null where the path or the string is missing`() {
        val pet = item(data = """{"item_data":{"rarity":"EPIC","stars":5},"extra":{"item_uuid":{"uuid":"abc"}}}""")
        assertEquals("EPIC", pet.rarity)
        assertEquals("abc", pet.string("extra", "item_uuid", "uuid"))
        assertNull(pet.string("item_data", "stars"))
        assertNull(pet.string("extra", "item_skin", "skinId"))
        assertEquals(setOf("uuid"), pet.objectAt("extra", "item_uuid")?.keys)
    }
}
