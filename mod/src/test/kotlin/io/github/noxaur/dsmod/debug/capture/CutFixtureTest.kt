package io.github.noxaur.dsmod.debug.capture

import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.ServerText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CutFixtureTest {
    private val dir = createTempDirectory("cut-fixture")
    private val sidebar = """{"t":0,"type":"sidebar","data":{"lines":[{"text":"Name: Carol"}]}}"""

    private fun text(vararg parts: String) = ServerText(parts.map { ServerSpan(it) })

    private fun say(line: String) = SystemMessage(text(line))

    private fun tab(vararg info: String) = TabList(listOf(text("End Island"), text("[MVP+] Alice [IRON]"), text("Info")) + info.map(::text))

    /** Writes a capture with [sidebar] first, then each event at its second. */
    private fun capture(vararg events: Pair<Int, GameEvent>): Path = dir.resolve("capture.jsonl").also { file ->
        file.writeLines(listOf(sidebar) + events.map { (second, event) -> captureLine(second * 1000L, event) })
    }

    private fun cut(capture: Path, seconds: ClosedFloatingPointRange<Double>, types: Set<String>, info: List<String> = emptyList()): String {
        val fixture = dir.resolve("fixture.jsonl")
        cutFixture(capture, fixture, seconds, types, info)
        readCapture(fixture)
        return fixture.readText()
    }

    @Test
    fun `a name split across spans becomes one alias and a chat line becomes hello`() {
        val capture = capture(
            1 to tab(),
            2 to SystemMessage(ServerText(ServerSpan("[MVP+] Al", 0x55FFFF), ServerSpan("ice", 0xFF5555), ServerSpan(": my secret"))),
            3 to say("Bob has obtained a thing"),
            4 to say("Alice: and again"),
        )
        val out = readCapture(dir.resolve("fixture.jsonl").also { cut(capture, 0.0..10.0, setOf("system")) })
        assertEquals(listOf("[MVP+] Player1: hello", "Player2 has obtained a thing", "Player1: hello"), out.map { (it.second as SystemMessage).text.plain })
        assertEquals(listOf("[MVP+] Player1", ": hello"), (out[0].second as SystemMessage).text.spans.map { it.text })
    }

    @Test
    fun `names inside a boss summary and the player's own name are redacted`() {
        val capture = capture(
            1 to tab(),
            2 to say("1st Damager [MVP+] Dave - 113.5M\n  Carol dealt the final blow."),
        )
        val out = cut(capture, 0.0..10.0, setOf("system"))
        assertFalse(listOf("Dave", "Carol", "Alice").any { it in out }, out)
        assertTrue("Player" in out)
    }

    @Test
    fun `a tab list row and a container title are redacted`() {
        val capture = capture(
            1 to ContainerOpened(5, text("Alice's chest"), 9),
            2 to tab("Area: The End"),
        )
        val out = cut(capture, 0.0..10.0, setOf("container", "tablist"))
        assertFalse("Alice" in out, out)
        assertTrue("Player1's chest" in out)
    }

    @Test
    fun `an item loses its uuid, signature and texture value`() {
        val data = buildJsonObject {
            put("uuid", "0123-4567")
            put("owner", "Alice")
            put("rarity", "EPIC")
            put("textures", buildJsonObject { put("value", "aGVsbG8="); put("signature", "c2ln") })
        }
        val item = Item("minecraft:player_head", 1, text("Alice's head"), listOf(text("Owner: Alice")), data)
        val capture = capture(1 to tab(), 2 to ContainerOpened(5, null, 9), 3 to SlotChanged(0, item))
        val out = cut(capture, 0.0..10.0, setOf("container"))
        assertFalse(listOf("Alice", "0123", "aGVs", "c2ln").any { it in out }, out)
        assertTrue("EPIC" in out)
    }

    @Test
    fun `the fixture begins with the tab list and the container as the window found them`() {
        val capture = capture(
            1 to tab("Area: Hub"),
            2 to tab("Area: The End"),
            3 to ContainerOpened(1, null, 9),
            4 to SlotChanged(2, Item("minecraft:stone", 1)),
            5 to SlotChanged(3, Item("minecraft:dirt", 1)),
            6 to SlotChanged(3, null),
            8 to say("inside"),
        )
        val events = readCapture(dir.resolve("fixture.jsonl").also { cut(capture, 7.0..9.0, setOf("system", "tablist", "container")) })
        assertEquals(listOf(TabList::class, ContainerOpened::class, SlotChanged::class, SystemMessage::class), events.map { it.second::class })
        assertEquals(2, (events[2].second as SlotChanged).slot)
        assertEquals("Area: The End", (events[0].second as TabList).rows.last().plain)
        assertEquals(7000L, events[0].first)
    }

    @Test
    fun `a tab list is kept only when a named info row changes`() {
        val capture = capture(
            1 to tab("Area: The End", "Dragon: 100%"),
            2 to tab("Area: The End", "Dragon: 90%"),
            3 to tab("Area: The End", "Dragon: 80%", "Eyes: 1"),
            4 to tab("Area: Hub", "Dragon: 70%"),
            5 to tab("Area: Hub", "Dragon: 60%"),
        )
        val out = readCapture(dir.resolve("fixture.jsonl").also { cut(capture, 1.5..9.0, setOf("tablist"), listOf("Area")) })
        assertEquals(listOf("Area: The End", "Area: Hub"), out.map { (it.second as TabList).rows.first { row -> row.plain.startsWith("Area") }.plain })
    }
}
