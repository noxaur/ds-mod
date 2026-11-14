package io.github.noxaur.dsmod.debug.capture

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
}
