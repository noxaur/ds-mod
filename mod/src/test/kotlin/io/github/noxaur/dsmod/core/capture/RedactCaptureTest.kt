package io.github.noxaur.dsmod.core.capture

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import kotlin.io.path.writeLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactCaptureTest {
    private val dir = Files.createTempDirectory("redact")

    private fun text(line: String) = ServerText(listOf(ServerSpan(line)))

    private fun redact(vararg lines: String, own: Set<String> = emptySet()): List<String> {
        val raw = dir.resolve("capture.jsonl").also { it.writeLines(lines.toList()) }
        val gz = dir.resolve("capture.jsonl.gz")
        redactCapture(raw, gz, own)
        return GZIPInputStream(Files.newInputStream(gz)).bufferedReader().readLines()
    }

    @Test
    fun `names, typed chat and skins are gone from events and raw records alike, and the file is gzipped`() {
        val out = redact(
            captureLine(1, TabList(listOf(text("The End"), text("[MVP+] Alice [IRON]"), text("Info"), text("Area: The End")))),
            captureLine(2, SystemMessage(text("[MVP+] Alice: my password is hunter2"))),
            captureLine(3, SystemMessage(text("Bob has obtained a thing"))),
            """{"t":4,"type":"chat","data":{"sender":"Alice","text":"Alice: my password is hunter2"}}""",
            """{"t":5,"type":"entities","data":{"full":true,"entries":{"7":{"entity":"minecraft:player","name":{"text":"Dave"}}}}}""",
            """{"t":6,"type":"skulls","data":{"entries":{"1,2,3":{"profile":{"name":"Alice","id":[1,2],"properties":[{"name":"textures","value":"aGVsbG8=","signature":"c2ln"}]}}}}}""",
            captureLine(7, Sidebar(lines = listOf(text("Name: Carol")))),
            """{"t":8,"type":"step","data":{"index":1,"do":"Warp to the End"}}""",
            own = setOf("Steve"),
        )
        val all = out.joinToString("\n")

        assertFalse(listOf("Alice", "Bob", "Dave", "Carol", "hunter2", "aGVs", "c2ln").any { it in all }, all)
        assertTrue("Player" in all)
        assertTrue("hello" in all, "a typed message becomes hello")
        assertFalse(out.any { "\"type\":\"chat\"" in it }, "the raw chat record is dropped")
        assertTrue("""{"index":1,"do":"Warp to the End"}""" in all, "what the brief said stays")
        assertEquals(7, out.size)
    }

    @Test
    fun `an NPC's colored label is not a player name`() {
        val out = redact(
            """{"t":1,"type":"entities","data":{"full":true,"entries":{"7":{"entity":"minecraft:player","name":{"text":"§e§lCLICK"}},"8":{"entity":"minecraft:player","name":{"text":"[VIP] Dave"}}}}}""",
            captureLine(2, SystemMessage(text("CLICK to open, Dave"))),
            own = setOf("Steve"),
        )

        assertEquals(listOf("CLICK to open, Player1"), plains(out).map { it.plain })
    }

    private fun plains(out: List<String>) = out.mapNotNull(::readCaptureLine).mapNotNull { (it.second as? SystemMessage)?.text }

    @Test
    fun `whatever follows a known name and a colon is typed text, in a line no rule knows`() {
        val tab = captureLine(1, TabList(listOf(text("The End"), text("[MVP+] Alice [IRON]"), text("Bob"), text("Info"))))
        val out = redact(
            tab,
            captureLine(2, SystemMessage(text("Alice whispers to you: secret123"))),
            captureLine(3, SystemMessage(ServerText(ServerSpan("From "), ServerSpan("[MVP+] Alice", 0x55FFFF), ServerSpan(": "), ServerSpan("meet me at ", italic = true), ServerSpan("spawn")))),
            captureLine(4, SystemMessage(text("You whisper to Bob: psst"))),
            captureLine(5, SystemMessage(text("Party > Bob joined the party"))),
            captureLine(6, SystemMessage(text("The Protector is rising: Tier 3/5"))),
            own = setOf("Steve"),
        )
        val all = out.joinToString("\n")

        assertFalse(listOf("Alice", "Bob", "secret123", "meet me", "spawn", "psst").any { it in all }, all)
        assertEquals(
            listOf(
                "Player1 whispers to you: hello",
                "From [MVP+] Player1: hello",
                "You whisper to Player2: hello",
                // A name with no typed message keeps its line, and a line that names nobody is not chat.
                "Party > Player2 joined the party",
                "The Protector is rising: Tier 3/5",
            ),
            plains(out).map { it.plain },
        )
        assertEquals("hello", plains(out)[1].spans.last().text, "the typed text is gone from the spans too")
    }

    @Test
    fun `a name split across the pieces of a component is gone from the component too`() {
        val out = redact(
            captureLine(1, TabList(listOf(text("The End"), text("Alice"), text("Info")))),
            """{"t":2,"type":"title","data":{"title":{"text":"Alice wins","component":{"text":"Ali","color":"red","extra":[{"text":"ce","color":"gold"},{"text":" wins"}]}}}}""",
            """{"t":3,"type":"entities","data":{"full":true,"entries":{"7":{"entity":"minecraft:armor_stand","equipment":{"head":{"components":{"minecraft:custom_name":{"text":"","extra":[{"text":"A"},{"text":"lic","extra":[{"text":"e"}]},"'s head"]}}}}}}}}""",
            """{"t":4,"type":"title","data":{"title":{"text":"Fight","component":{"text":"Fi","extra":[{"text":"ght","bold":true}]}}}}""",
        )

        assertFalse(out.any { "Ali" in it || "lic" in it }, out.toString())
        assertTrue("""{"title":{"text":"Player1 wins","component":"Player1 wins"}}""" in out[1], out[1])
        assertTrue(""""minecraft:custom_name":"Player1's head"""" in out[2], out[2])
        assertTrue(""""component":{"text":"Fi","extra":[{"text":"ght","bold":true}]}""" in out[3], "a component that names nobody keeps its pieces")
    }

    @Test
    fun `the player's own name goes even when no line shows where it sits`() {
        val out = redact("""{"t":1,"type":"status","data":{"note":"Steve at 1,2,3"}}""", own = setOf("Steve"))

        assertFalse("Steve" in out.single())
    }

    @Test
    fun `a damage summary keeps its figures, and only the names go`() {
        val summary = "                1st Damager [VIP] Alice - 80.8M\n                2nd Damager [MVP+] Bob - 33.8M\n\n                   Your Damage: 33.8M (Position #2)"
        val out = redact(
            captureLine(1, TabList(listOf(text("The End"), text("[VIP] Alice"), text("[MVP+] Bob"), text("Info")))),
            captureLine(2, SystemMessage(text(summary))),
            captureLine(3, SystemMessage(text("[VIP] Alice [IRON]: hello there"))),
            captureLine(4, SystemMessage(text("Bob \u272F: nice"))),
            own = setOf("Steve"),
        )

        assertEquals(
            listOf(summary.replace("Alice", "Player1").replace("Bob", "Player2"), "[VIP] Player1 [IRON]: hello", "Player2 \u272F: hello"),
            plains(out).map { it.plain },
        )
    }

    @Test
    fun `a skull's profile id and an unseen name in its profile are gone`() {
        val out = redact(
            """{"t":1,"type":"skulls","data":{"entries":{"1,2,3":{"profile":{"name":"Zelda","id":[1,2,3,4]}}}}}""",
            """{"t":2,"type":"itemcomponents","data":{"entries":{"5":{"minecraft:profile":{"name":"Link","id":"1-2-3","properties":[]}}}}}""",
            """{"t":3,"type":"status","data":{"note":"Zelda met Link","id":[9,9]}}""",
        )
        val all = out.joinToString("\n")

        assertFalse(listOf("Zelda", "Link", "1,2,3,4", "1-2-3").any { it in all.replace("\"1,2,3\"", "") }, all)
        assertTrue(""""id":[]""" in out[0], out[0])
        assertTrue(""""id":""""" in out[1], out[1])
        assertTrue("[9,9]" in out[2], "an id outside a profile stays")
    }

    @Test
    fun `a profile name shorter than three letters or empty is not learned`() {
        val out = redact("""{"t":1,"type":"skulls","data":{"entries":{"a":{"profile":{"name":"","id":[1]}},"b":{"profile":{"name":"Al","id":[2]}}}}}""", """{"t":2,"type":"status","data":{"note":"Al is at 1"}}""")

        assertTrue("Al is at 1" in out[1], out[1])
    }
}
