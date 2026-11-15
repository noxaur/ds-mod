package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Skulls
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemSightingsTest {
    private val sightings = ItemSightings { setOf("Steve", "Zed") }
    private val uuid = "123e4567-e89b-12d3-a456-426614174000"

    private fun text(line: String) = ServerText(listOf(ServerSpan(line)))

    private fun json(source: String) = Json.parseToJsonElement(source).jsonObject

    private fun item(
        data: String = """{"item_data":{"item_id":"scylla","uuid":"$uuid","rarity":"LEGENDARY"}}""",
        lore: List<ServerText> = listOf(text("Damage: +300")),
        glint: Boolean = true,
    ) = Item("minecraft:iron_sword", 1, text("Scylla"), lore, json(data), glint)

    @Test
    fun `an item of the server's is a sighting, and one without an id is not`() {
        sightings.see(item())
        sightings.see(item(data = """{"other":1}"""))

        val sighting = sightings.take().single()
        assertEquals("scylla", sighting.entry)
        assertEquals("minecraft:iron_sword", sighting.material)
        assertTrue(sighting.glint)
    }

    @Test
    fun `only the allowed keys of custom_data go, and no copy's id`() {
        val data = """{"item_data":{"item_id":"scylla","uuid":"$uuid","rarity":"EPIC"},"item_stats":{"damage":300},
            "item_modifiers":{"reforge":"fierce"},"extra":{"item_uuid":"$uuid","item_skin":{"skinId":"gold"},"owner":"Zed"},
            "abilities":["rage"],"private":{"note":"Steve"},"auction":{"seller":"Zed"}}"""
        sightings.see(item(data = data))

        val sent = sightings.take().single()
        assertEquals("gold", sent.skinId)
        assertEquals(
            json("""{"item_data":{"item_id":"scylla","rarity":"EPIC"},"item_stats":{"damage":300},"item_modifiers":{"reforge":"fierce"},"extra":{"item_skin":{"skinId":"gold"}},"abilities":["rage"]}"""),
            sent.data,
        )
    }

    @Test
    fun `an unknown key inside item_data or extra is dropped, even one that holds a name`() {
        val data = """{"item_data":{"item_id":"scylla","owner":"Alexander","rarity":"EPIC"},
            "extra":{"item_dye":{"id":"red"},"crafted_by":"Alexander","item_skin":{"skinId":"gold","buyer":"Alexander"}}}"""
        sightings.see(item(data = data))

        val sent = sightings.take().single()
        assertEquals(
            json("""{"item_data":{"item_id":"scylla","rarity":"EPIC"},"extra":{"item_dye":{"id":"red"},"item_skin":{"skinId":"gold"}}}"""),
            sent.data,
        )
        assertFalse("Alexander" in sent.data.toString())
    }

    @Test
    fun `a sighting holds no known player name and no uuid`() {
        val data = """{"item_data":{"item_id":"scylla","uuid":"$uuid"},"extra":{"item_uuid":"$uuid","crafted":"Zed made $uuid"}}"""
        sightings.see(item(data = data, lore = listOf(text("Crafted by Steve"), text("Owner: Zed"), text("Id: $uuid"))))

        val sent = sightings.take().single()
        val everything = sent.toString() + sent.lore.joinToString { it.plain } + sent.data
        assertFalse("Steve" in everything || "Zed" in everything, everything)
        assertFalse(uuid in everything, everything)
        assertFalse(Regex("[0-9a-f]{8}-[0-9a-f]{4}-").containsMatchIn(everything), everything)
    }

    @Test
    fun `a head keeps its skin url and loses the player it names`() {
        val property = Base64.getEncoder().encodeToString(
            """{"timestamp":1,"profileId":"$uuid","profileName":"Zed","textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc","metadata":{"model":"slim"}}}}""".toByteArray(),
        )
        val cut = assertNotNull(Skulls.withoutPlayer(property))
        val decoded = String(Base64.getDecoder().decode(cut))

        assertEquals("""{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc"}}}""", decoded)
        assertNull(Skulls.withoutPlayer("not base64!"))
        assertNull(Skulls.withoutPlayer(Base64.getEncoder().encodeToString("""{"textures":{}}""".toByteArray())))
    }

    @Test
    fun `each distinct look goes once, by entry, glint, skin, texture and lore`() {
        sightings.see(item())
        sightings.see(item().copy(count = 5))
        sightings.see(item(glint = false))
        sightings.see(item(lore = listOf(text("Damage: +310"))))
        sightings.see(item(data = """{"item_data":{"item_id":"scylla"},"extra":{"item_skin":{"skinId":"gold"}}}"""))
        assertEquals(4, sightings.take().size)

        sightings.see(item())
        assertFalse(sightings.waiting, "a look that went is not collected again")
    }

    @Test
    fun `a batch is capped at fifty and a look that does not fit the backend is left out`() {
        repeat(80) { sightings.see(item(lore = listOf(text("Damage: +$it")))) }
        assertEquals(50, sightings.take().size)

        val long = ItemSightings { emptySet() }
        long.see(item(data = """{"item_data":{"item_id":"${"x".repeat(129)}"}}"""))
        long.see(item().copy(skull = "x".repeat(4097)))
        long.see(item(lore = List(120) { text("line $it") }))
        assertEquals(80, long.take().single().lore.size)
    }
}
