package io.github.noxaur.dsmod.debug.capture

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.ServerText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptureWriterTest {
    private val dir = Files.createTempDirectory("dsmod-capture")
    private val drop = SystemMessage(ServerText(ServerSpan("RARE DROP! ", 0xFFAA00, bold = true), ServerSpan("Summoning Eye")))

    @Test
    fun `writes one JSON line per record in order`() {
        var now = 1_000L
        val writer = CaptureWriter(dir) { now }

        writer.record("sidebar") { buildJsonObject { put("title", "first") } }
        now = 2_000L
        writer.record(1_500L, drop)
        writer.close()

        assertEquals("1970-01-01T00-00-01Z.jsonl", writer.file.fileName.toString())
        assertEquals(
            listOf(
                """{"t":1000,"type":"sidebar","data":{"title":"first"}}""",
                """{"t":1500,"type":"system","data":{"text":{"plain":"RARE DROP! Summoning Eye",""" +
                    """"spans":[{"text":"RARE DROP! ","color":"#FFAA00","bold":true},{"text":"Summoning Eye"}]}}}""",
            ),
            writer.file.readLines(),
        )
    }

    @Test
    fun `readCapture gives back the events and skips raw records`() {
        val writer = CaptureWriter(dir) { 1_000L }
        writer.record("sidebar") { buildJsonObject { put("title", "first") } }
        writer.record(1_500L, drop)
        writer.close()

        assertEquals(listOf(1_500L to drop), readCapture(writer.file))
    }

    @Test
    fun `a raw record cannot take the type of an event`() {
        CaptureWriter(dir).use { writer ->
            assertFailsWith<IllegalStateException> { writer.record("system") { buildJsonObject { } } }
        }
    }
}
