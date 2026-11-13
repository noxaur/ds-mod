package io.github.noxaur.dsmod.debug.capture

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureWriterTest {
    @Test
    fun `writes one JSON line per event in order`() {
        val dir = Files.createTempDirectory("dsmod-capture")
        var now = 1_000L
        val writer = CaptureWriter(dir) { now }

        writer.record("system") { buildJsonObject { put("text", "first") } }
        now = 2_000L
        writer.record("chat") { buildJsonObject { put("text", "second") } }
        writer.close()

        assertEquals("1970-01-01T00-00-01Z.jsonl", writer.file.fileName.toString())
        assertEquals(
            listOf(
                """{"t":1000,"type":"system","data":{"text":"first"}}""",
                """{"t":2000,"type":"chat","data":{"text":"second"}}""",
            ),
            writer.file.readLines(),
        )
    }
}
