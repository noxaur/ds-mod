package io.github.noxaur.dsmod.debug

import io.github.noxaur.dsmod.debug.brief.Brief
import io.github.noxaur.dsmod.debug.brief.BriefRun
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BriefTest {
    private fun read(json: String) = Brief.read(Files.createTempFile("brief", ".json").also { it.writeText(json) })

    private val brief = read(
        """{"name": "Golem tiers", "steps": [
            {"do": "Warp to the End"},
            {"do": "Stand still next to the Protector until it spawns", "note": "Do not hit it.", "seconds": 90},
            {"do": "Kill it"}
        ]}""",
    )

    private fun BriefRun.lines(now: Long) = hud(now, "]", null).let { block -> listOf(block.header.plain) + block.rows.map { it.text.plain } }

    @Test
    fun `reads the steps in order with their note and timer`() {
        assertEquals("Golem tiers", brief.name)
        assertEquals(listOf("Warp to the End", "Stand still next to the Protector until it spawns", "Kill it"), brief.steps.map { it.action })
        assertEquals(Brief.Step("Stand still next to the Protector until it spawns", "Do not hit it.", 90), brief.steps[1])
    }

    @Test
    fun `rejects a brief that would lose what the agent asked for`() {
        assertFails { read("""{"name": "A", "steps": [{"do": "B", "secs": 5}]}""") }
        assertFails { read("""{"name": "A", "steps": []}""") }
        assertFails { read("""{"name": " ", "steps": [{"do": "B"}]}""") }
        assertFails { read("""{"name": "A", "steps": [{"do": "B", "seconds": 0}]}""") }
    }

    @Test
    fun `waits for the developer, then shows the step, its note and its timer`() {
        val run = BriefRun(brief)
        assertEquals(
            listOf("Golem tiers", "1. Warp to the End", "2. Stand still next to the Protector until it", "   spawns", "3. Kill it", "Press ] to start recording."),
            run.lines(0),
        )
        assertFalse(run.tick(500_000))

        run.advance(1_000)
        assertEquals("Press ] when done.", run.lines(1_000).last())
        run.advance(2_000)
        assertEquals(
            listOf(
                "Golem tiers", "1. Warp to the End", "2. Stand still next to the Protector until it 1:30", "   spawns", "   Do not hit it.",
                "3. Kill it", "Press ] to skip the timer.",
            ),
            run.lines(2_000),
        )
        assertEquals(1, run.remaining(91_001))
    }

    @Test
    fun `a timed step moves on by itself and the last step ends the run`() {
        val run = BriefRun(brief)
        run.advance(0)
        run.advance(0)
        assertFalse(run.tick(89_999))
        assertTrue(run.tick(90_000))
        assertEquals("""{"index":3,"do":"Kill it"}""", run.record().toString())
        assertFalse(run.tick(900_000))

        assertTrue(run.advance(100_000))
        assertTrue(run.done)
        assertEquals("""{"done":true}""", run.record().toString())
        assertFalse(run.advance(100_000))
        assertEquals("Saved a.jsonl", run.hud(100_000, "]", "a.jsonl").rows.last().text.plain)
    }
}
