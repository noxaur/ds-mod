package io.github.noxaur.dsmod.core.capture

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.ServerText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
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

        writer.record(RawRecord.BOSSBARS) { buildJsonObject { put("title", "first") } }
        now = 2_000L
        writer.record(1_500L, drop)
        writer.close()

        assertEquals("1970-01-01T00-00-01Z.jsonl", writer.file.fileName.toString())
        assertEquals(
            listOf(
                """{"t":1000,"type":"bossbars","data":{"title":"first"}}""",
                """{"t":1500,"type":"system","data":{"text":{"plain":"RARE DROP! Summoning Eye",""" +
                    """"spans":[{"text":"RARE DROP! ","color":"#FFAA00","bold":true},{"text":"Summoning Eye"}]}}}""",
            ),
            writer.file.readLines(),
        )
    }

    @Test
    fun `readCapture gives back the events and skips raw records`() {
        val writer = CaptureWriter(dir) { 1_000L }
        writer.record(RawRecord.BOSSBARS) { buildJsonObject { put("title", "first") } }
        writer.record(1_500L, drop)
        writer.close()

        assertEquals(listOf(1_500L to drop), readCapture(writer.file))
    }

    @Test
    fun `close waits for every record, however long the writer takes`() {
        val writer = CaptureWriter(dir)
        writer.record(RawRecord.BOSSBARS) {
            Thread.sleep(300)
            buildJsonObject { put("title", "slow") }
        }
        repeat(100) { writer.record(it.toLong(), drop) }

        writer.close()

        assertEquals(101, writer.file.readLines().size)
    }

    @Test
    fun `close says when the writer did not finish in time`() {
        val release = CountDownLatch(1)
        val writer = CaptureWriter(dir, closeMillis = 50)
        writer.record(RawRecord.BOSSBARS) {
            release.await()
            buildJsonObject { }
        }

        try {
            assertFailsWith<CaptureIncompleteException> { writer.close() }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `close says when a record was lost`() {
        val writer = CaptureWriter(dir)
        writer.record(RawRecord.BOSSBARS) { error("this record cannot be built") }
        writer.record(1_500L, drop)

        assertFailsWith<CaptureIncompleteException> { writer.close() }
        assertEquals(1, writer.file.readLines().size, "the records after it are still written")
    }

    @Test
    fun `no raw record takes the type of an event`() {
        assertEquals(emptyList(), RawRecord.entries.filter { it.type in eventTypes }, "each type has one producer, so delete the raw record that became an event")
    }
}
