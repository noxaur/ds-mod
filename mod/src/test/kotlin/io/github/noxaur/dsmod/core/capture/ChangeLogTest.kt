package io.github.noxaur.dsmod.core.capture

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeLogTest {
    private val recorded = mutableListOf<String>()
    private val log = ChangeLog { type, data -> recorded += "$type $data" }

    private fun text(value: String) = buildJsonObject { put("text", value) }

    @Test
    fun `offer records a value once until it changes`() {
        log.offer("sidebar", text("a"))
        log.offer("sidebar", text("a"))
        log.offer("sidebar", text("b"))

        assertEquals(listOf("""sidebar {"text":"a"}""", """sidebar {"text":"b"}"""), recorded)
    }

    @Test
    fun `offer with force records an unchanged value`() {
        log.offer("actionbar", text("a"))
        log.offer("actionbar", text("a"), force = true)

        assertEquals(2, recorded.size)
    }

    @Test
    fun `offerEntries records everything first, then only changed and removed entries`() {
        val header = buildJsonObject { put("id", 1) }
        log.offerEntries("container", header, mapOf("0" to JsonPrimitive("sword"), "1" to JsonPrimitive("bow")))
        log.offerEntries("container", header, mapOf("0" to JsonPrimitive("sword"), "1" to JsonPrimitive("bow")))
        log.offerEntries("container", header, mapOf("0" to JsonPrimitive("axe")))

        assertEquals(
            listOf(
                """container {"id":1,"full":true,"entries":{"0":"sword","1":"bow"}}""",
                """container {"id":1,"full":false,"entries":{"0":"axe","1":null}}""",
            ),
            recorded,
        )
    }

    @Test
    fun `offerEntries starts over when the header changes`() {
        log.offerEntries("container", buildJsonObject { put("id", 1) }, mapOf("0" to JsonPrimitive("sword")))
        log.offerEntries("container", buildJsonObject { put("id", 2) }, mapOf("0" to JsonPrimitive("sword")))

        assertEquals("""container {"id":2,"full":true,"entries":{"0":"sword"}}""", recorded.last())
    }

    @Test
    fun `offerEntries records an empty first snapshot`() {
        log.offerEntries("nametags", JsonObject(emptyMap()), emptyMap())

        assertEquals(listOf("""nametags {"full":true,"entries":{}}"""), recorded)
    }

    @Test
    fun `offerEntries sees a change in a map its caller refills`() {
        val header = buildJsonObject { }
        val blocks = LinkedHashMap<String, JsonElement>()
        blocks["1,2,3"] = JsonPrimitive("minecraft:end_portal_frame[eye=false]")
        blocks["4,5,6"] = JsonPrimitive("minecraft:obsidian")
        log.offerEntries("blockstates", header, blocks)
        blocks.clear()
        blocks["1,2,3"] = JsonPrimitive("minecraft:end_portal_frame[eye=true]")
        log.offerEntries("blockstates", header, blocks)
        log.offerEntries("blockstates", header, blocks)

        assertEquals(
            listOf(
                """blockstates {"full":true,"entries":{"1,2,3":"minecraft:end_portal_frame[eye=false]","4,5,6":"minecraft:obsidian"}}""",
                """blockstates {"full":false,"entries":{"1,2,3":"minecraft:end_portal_frame[eye=true]","4,5,6":null}}""",
            ),
            recorded,
        )
    }

    @Test
    fun `offerChanges records everything first, then only what the caller says changed`() {
        log.offerChanges("entities", mapOf("1" to JsonPrimitive("zombie"), "2" to JsonPrimitive("pig")))
        log.offerChanges("entities", emptyMap())
        log.offerChanges("entities", mapOf("1" to JsonPrimitive("husk"), "2" to JsonNull))

        assertEquals(
            listOf(
                """entities {"full":true,"entries":{"1":"zombie","2":"pig"}}""",
                """entities {"full":false,"entries":{"1":"husk","2":null}}""",
            ),
            recorded,
        )
    }

    @Test
    fun `offerChanges records an empty first offer`() {
        log.offerChanges("entities", emptyMap())

        assertEquals(listOf("""entities {"full":true,"entries":{}}"""), recorded)
    }
}
