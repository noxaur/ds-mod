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
    private val log = ChangeLog { type, data -> recorded += "${type.type} $data" }

    private fun text(value: String) = buildJsonObject { put("text", value) }

    @Test
    fun `offer records a value once until it changes`() {
        log.offer(RawRecord.TITLE, text("a"))
        log.offer(RawRecord.TITLE, text("a"))
        log.offer(RawRecord.TITLE, text("b"))

        assertEquals(listOf("""title {"text":"a"}""", """title {"text":"b"}"""), recorded)
    }

    @Test
    fun `offer with force records an unchanged value`() {
        log.offer(RawRecord.TITLE, text("a"))
        log.offer(RawRecord.TITLE, text("a"), force = true)

        assertEquals(2, recorded.size)
    }

    @Test
    fun `offerEntries records everything first, then only changed and removed entries`() {
        val header = buildJsonObject { put("id", 1) }
        log.offerEntries(RawRecord.ITEMCOMPONENTS, header, mapOf("0" to JsonPrimitive("sword"), "1" to JsonPrimitive("bow")))
        log.offerEntries(RawRecord.ITEMCOMPONENTS, header, mapOf("0" to JsonPrimitive("sword"), "1" to JsonPrimitive("bow")))
        log.offerEntries(RawRecord.ITEMCOMPONENTS, header, mapOf("0" to JsonPrimitive("axe")))

        assertEquals(
            listOf(
                """itemcomponents {"id":1,"full":true,"entries":{"0":"sword","1":"bow"}}""",
                """itemcomponents {"id":1,"full":false,"entries":{"0":"axe","1":null}}""",
            ),
            recorded,
        )
    }

    @Test
    fun `offerEntries starts over when the header changes`() {
        log.offerEntries(RawRecord.ITEMCOMPONENTS, buildJsonObject { put("id", 1) }, mapOf("0" to JsonPrimitive("sword")))
        log.offerEntries(RawRecord.ITEMCOMPONENTS, buildJsonObject { put("id", 2) }, mapOf("0" to JsonPrimitive("sword")))

        assertEquals("""itemcomponents {"id":2,"full":true,"entries":{"0":"sword"}}""", recorded.last())
    }

    @Test
    fun `offerEntries records an empty first snapshot`() {
        log.offerEntries(RawRecord.SKULLS, JsonObject(emptyMap()), emptyMap())

        assertEquals(listOf("""skulls {"full":true,"entries":{}}"""), recorded)
    }

    @Test
    fun `offerEntries sees a change in a map its caller refills`() {
        val header = buildJsonObject { }
        val blocks = LinkedHashMap<String, JsonElement>()
        blocks["1,2,3"] = JsonPrimitive("minecraft:end_portal_frame[eye=false]")
        blocks["4,5,6"] = JsonPrimitive("minecraft:obsidian")
        log.offerEntries(RawRecord.BLOCKSTATES, header, blocks)
        blocks.clear()
        blocks["1,2,3"] = JsonPrimitive("minecraft:end_portal_frame[eye=true]")
        log.offerEntries(RawRecord.BLOCKSTATES, header, blocks)
        log.offerEntries(RawRecord.BLOCKSTATES, header, blocks)

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
        log.offerChanges(RawRecord.ENTITIES, mapOf("1" to JsonPrimitive("zombie"), "2" to JsonPrimitive("pig")))
        log.offerChanges(RawRecord.ENTITIES, emptyMap())
        log.offerChanges(RawRecord.ENTITIES, mapOf("1" to JsonPrimitive("husk"), "2" to JsonNull))

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
        log.offerChanges(RawRecord.ENTITIES, emptyMap())

        assertEquals(listOf("""entities {"full":true,"entries":{}}"""), recorded)
    }
}
