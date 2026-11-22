package io.github.noxaur.dsmod.core.brief

import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.brief.BriefRun
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

    @Test
    fun `a run with a time limit ends that long after it opened, unless it is done`() {
        val run = BriefRun(brief, 1_000, 600)
        assertFalse(run.timedOut(600_999))
        assertTrue(run.timedOut(601_000))
        run.finish(10_000)
        assertFalse(run.timedOut(900_000))
        assertFalse(BriefRun(brief, 0, null).timedOut(Long.MAX_VALUE))
    }

    @Test
    fun `a run can end before its last step`() {
        val run = BriefRun(brief)
        run.advance(0)
        run.finish(5)
        assertTrue(run.done)
        assertEquals("""{"done":true}""", run.record().toString())
    }

    @Test
    fun `two presses in one tick record both steps, each as it started`() {
        val run = BriefRun(brief)

        assertEquals(
            listOf("""{"index":1,"do":"Warp to the End"}""", """{"index":2,"do":"Stand still next to the Protector until it spawns"}"""),
            run.step(1_000, presses = 2).map { it.toString() },
        )
        assertEquals(emptyList(), run.step(2_000, presses = 0))
        assertEquals(listOf("""{"index":3,"do":"Kill it"}""", """{"done":true}"""), run.step(91_000, presses = 1).map { it.toString() })
        assertEquals(emptyList(), run.step(92_000, presses = 3))
    }

    @Test
    fun `a run with a time limit shows its time left as the last row, and one without shows none`() {
        val limited = BriefRun(brief, openedAt = 1_000, timeoutSeconds = 600)
        assertEquals("Time left 10:00", limited.lines(1_000).last())
        assertEquals("Time left 5:01", limited.lines(300_000).last())
        limited.advance(2_000)
        assertEquals("Time left 9:59", limited.lines(2_000).last())
        limited.finish(3_000)
        assertEquals("Done.", limited.lines(3_000).last())

        assertEquals("Press ] to start recording.", BriefRun(brief).lines(0).last())
    }
}
