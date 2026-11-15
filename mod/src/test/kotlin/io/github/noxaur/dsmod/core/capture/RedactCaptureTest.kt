package io.github.noxaur.dsmod.core.capture

import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
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

    @Test
    fun `a plain name on a player entity, which may be an NPC's, goes only from that entity`() {
        val out = redact(
            """{"t":1,"type":"entities","data":{"full":true,"entries":{"7":{"entity":"minecraft:player","name":{"text":"Dungeons","component":{"text":"Dungeons"}}},"8":{"entity":"minecraft:player","name":{"text":"Dqwerty"}}}}}""",
            """{"t":2,"type":"swing","data":{"target":{"entity":"minecraft:player","name":"Dqwerty","x":1.0}}}""",
            captureLine(3, SystemMessage(text("Dungeons Booster (3 Hours) by [OWNER] Erin"))),
        )
        val all = out.joinToString("\n")

        assertFalse("Dqwerty" in all || "Erin" in all, all)
        assertTrue(""""name":{"text":"Player""" in out[0] && "\"Dungeons\"" !in out[0], out[0])
        assertEquals(listOf("Dungeons Booster (3 Hours) by [OWNER] Player3"), plains(out).map { it.plain })
    }

    @Test
    fun `what the server's characters say is not typed, and their names are not players'`() {
        val out = redact(
            captureLine(1, SystemMessage(text("[NPC] Mort: Good luck."))),
            captureLine(2, SystemMessage(text("[BOSS] Kuudra: You will never catch me!"))),
            captureLine(3, SystemMessage(text("Mort and Kuudra wait"))),
        )

        assertEquals(listOf("[NPC] Mort: Good luck.", "[BOSS] Kuudra: You will never catch me!", "Mort and Kuudra wait"), plains(out).map { it.plain })
    }

    @Test
    fun `a speaker on a later line of a message is typed text there, and the other lines stay`() {
        val out = redact(
            captureLine(1, TabList(listOf(text("The End"), text("Alice"), text("Info")))),
            captureLine(2, SystemMessage(text("✦ ACTIVE BOOSTERS ✦\nEveryone: +40% dungeon drops.\nAlice says: psst\nActivator: +300%"))),
        )

        assertEquals(listOf("✦ ACTIVE BOOSTERS ✦\nEveryone: +40% dungeon drops.\nPlayer1 says: hello\nActivator: +300%"), plains(out).map { it.plain })
    }

    @Test
    fun `an auction's seller goes, and so does a name behind a color code in an older raw record`() {
        val out = redact(
            captureLine(1, SlotChanged(3, Item("minecraft:stick", 1, text("Stick"), listOf(text("Seller: [VIP] Frank"), text("Buy it now: 5"))))),
            """{"t":2,"type":"container","data":{"entries":{"4":{"lore":["§7Seller: §f§aGrace","§7Bids: §6Frank"]}}}}""",
            """{"t":3,"type":"bossbars","data":{"bars":[{"text":"§aG§br§ca§dc§ee's bar"}]}}""",
        )
        val all = out.joinToString("\n")

        assertFalse(listOf("Frank", "Grace", "§aG").any { it in all }, all)
        assertTrue("Buy it now: 5" in out[0] && "Seller: [VIP] Player1" in out[0], out[0])
        assertTrue(""""Seller: Player2","Bids: Player1"""" in out[1], out[1])
        assertTrue(""""Player2's bar"""" in out[2], out[2])
    }

    @Test
    fun `a player named what the server calls itself or a place is replaced only where a name sits`() {
        val out = redact(
            captureLine(1, TabList(listOf(text("     End Island (64) 1/4     "), text("[VIP] Island"), text("End"), text("Info")))),
            captureLine(2, Sidebar(text("DragSim"), listOf(text("⏣ Dragon's Den")))),
            captureLine(3, SlotChanged(3, Item("minecraft:stick", 1, text("Stick"), listOf(text("Seller: [MOD] DragSim"))))),
            captureLine(4, SystemMessage(text("[MOD] DragSim: welcome, End"))),
            captureLine(5, SystemMessage(text("Warping to End Island on DragSim…"))),
            captureLine(6, SystemMessage(text("» End placed a Summoning Eye! (1/8)"))),
        )

        assertEquals(
            listOf("[MOD] Player3: hello", "Warping to End Island on DragSim…", "» Player2 placed a Summoning Eye! (1/8)"),
            plains(out).map { it.plain },
        )
        assertTrue("[VIP] Player1" in out[0] && "Player2" in out[0] && "End Island (64)" in out[0], out[0])
        assertTrue("\"DragSim\"" in out[1] && "Seller: [MOD] Player3" in out[2], out[1] + out[2])
    }

    @Test
    fun `an older capture's chat line is dropped, and its other old events are redacted as raw data`() {
        val out = redact(
            captureLine(1, TabList(listOf(text("The End"), text("Alice"), text("Info")))),
            """{"t":2,"type":"system","data":{"text":"§7Alice§f: my secret"}}""",
            """{"t":3,"type":"death","data":{"entity":"minecraft:zombie","name":{"text":"Alice's Zombie"}}}""",
        )

        assertEquals(2, out.size, out.toString())
        assertTrue(""""name":{"text":"Player1's Zombie"}""" in out[1], out[1])
    }

    @Test
    fun `a word one capture shows as the server's own is no sure name across captures`() {
        val withTitle = dir.resolve("title.jsonl").also { it.writeLines(listOf(captureLine(1, Sidebar(text("DragSim"), emptyList())))) }
        val withSeller = dir.resolve("seller.jsonl").also {
            it.writeLines(listOf(captureLine(1, SlotChanged(3, Item("minecraft:stick", 1, lore = listOf(text("Seller: [MOD] DragSim"), text("Seller: Frank")))))))
        }

        assertEquals(setOf("DragSim", "Frank"), PlayerNames.of(withSeller))
        assertEquals(setOf("Frank"), PlayerNames.of(withTitle, withSeller))
    }

    @Test
    fun `an entity's uuid goes in either form, and so does a split name with a color code in front`() {
        val out = redact(
            captureLine(1, TabList(listOf(text("The End"), text("Alice"), text("Info")))),
            """{"t":2,"type":"title","data":{"title":{"text":"x","component":{"text":"§aAli","extra":["ce"],"hover_event":{"id":"minecraft:player","uuid":[5,6,7,8]}}}}}""",
            """{"t":3,"type":"title","data":{"title":{"text":"x","component":{"text":"","hover_event":{"uuid":"0-1-2"}}}}}""",
        )

        assertFalse(listOf("Ali", "5,6,7,8", "0-1-2").any { it in out[1] + out[2] }, out.toString())
        assertTrue(""""component":"Player1"""" in out[1], out[1])
    }

    @Test
    fun `an NPC that shares a player's name says what it said, and a player called like an alias is renamed`() {
        val out = redact(
            captureLine(1, TabList(listOf(text("The End"), text("Mort"), text("Player1"), text("Info")))),
            captureLine(2, SystemMessage(text("[NPC] Mort: Good luck."))),
            captureLine(3, SystemMessage(text("Player1: my secret"))),
        )

        assertEquals(listOf("[NPC] Player1: Good luck.", "Player2: hello"), plains(out).map { it.plain })
    }

    @Test
    fun `the server's own head textures keep their names`() {
        val out = redact("""{"t":1,"type":"skulls","data":{"entries":{"1,2,3":{"profile":{"name":"h1dc2c096de8131d","id":[1,2]}}}}}""")

        assertTrue("h1dc2c096de8131d" in out.single(), out.single())
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
