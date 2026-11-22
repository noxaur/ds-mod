package io.github.noxaur.dsmod.debug.capture

import io.github.noxaur.dsmod.core.capture.Captured
import io.github.noxaur.dsmod.core.capture.captureLine
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Boss
import io.github.noxaur.dsmod.core.game.EntityFilter
import io.github.noxaur.dsmod.core.game.EntityRenamed
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.harness.readCapture
import io.github.noxaur.dsmod.harness.chat
import io.github.noxaur.dsmod.harness.text
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CutFixtureTest {
    private val dir = createTempDirectory("cut-fixture")
    private val sidebar = captureLine(0, Sidebar(lines = listOf(text("Name: Carol"))))

    private fun say(line: String) = chat(line)

    private fun tab(vararg info: String) = TabList(listOf(text("End Island"), text("[MVP+] Alice [IRON]"), text("Info")) + info.map { text(it) })

    private fun capture(vararg events: Pair<Int, GameEvent>): Path = dir.resolve("capture.jsonl").also { file ->
        file.writeLines(listOf(sidebar) + events.map { (second, event) -> captureLine(second * 1000L, event) })
    }

    private fun cut(
        capture: Path,
        seconds: ClosedFloatingPointRange<Double>,
        types: Set<String>,
        info: List<String> = emptyList(),
        area: Area = Area.ANYWHERE,
        entities: List<EntityFilter> = emptyList(),
    ): String {
        val fixture = dir.resolve("fixture.jsonl")
        cutFixture(capture, fixture, seconds, types, area, info, entities = entities)
        readCapture(fixture)
        return fixture.readText()
    }

    private fun cutRead(
        capture: Path,
        seconds: ClosedFloatingPointRange<Double>,
        types: Set<String>,
        info: List<String> = emptyList(),
        area: Area = Area.ANYWHERE,
        entities: List<EntityFilter> = emptyList(),
    ) = readCapture(dir.resolve("fixture.jsonl").also { cut(capture, seconds, types, info, area, entities) })

    private fun assertKinds(events: List<Captured>, vararg kinds: KClass<*>) = assertEquals(kinds.toList(), events.map { it.event::class })

    private val protector = EntitySeen(7, "minecraft:iron_golem", text("[Lv100] Endstone Protector 10M/10M"))

    private fun bossCut(capture: Path, seconds: ClosedFloatingPointRange<Double>) =
        cutRead(capture, seconds, emptySet(), entities = Boss.FILTERS).map { it.event }

    @Test
    fun `an entity cut keeps only the entities the feature reads, from those held at the start to the player leaving`() {
        val capture = capture(
            1 to EntitySeen(5, "minecraft:armor_stand", text("[Lvl 100] Black Cat")),
            2 to protector,
            4 to EntityRenamed(7, text("[Lv100] Endstone Protector 9M/10M")),
            5 to LeftServer,
        )
        assertEquals(listOf(protector, EntityRenamed(7, text("[Lv100] Endstone Protector 9M/10M")), LeftServer), bossCut(capture, 3.0..10.0))
    }

    @Test
    fun `a window after the player left starts with no entity`() {
        val capture = capture(1 to protector, 2 to LeftServer, 4 to say("Back again"))
        assertEquals(emptyList(), bossCut(capture, 3.0..10.0))
    }

    @Test
    fun `a name split across spans becomes one alias and a chat line becomes hello`() {
        val capture = capture(
            1 to tab(),
            2 to SystemMessage(text(ServerSpan("[MVP+] Al", 0x55FFFF), ServerSpan("ice", 0xFF5555), ServerSpan(": my secret"))),
            3 to say("Bob has obtained a thing"),
            4 to say("Alice: and again"),
        )
        val out = cutRead(capture, 0.0..10.0, setOf("system"))
        assertEquals(listOf("[MVP+] Player1: hello", "Player2 has obtained a thing", "Player1: hello"), out.map { (it.event as SystemMessage).text.plain })
        assertEquals(listOf("[MVP+] Player1", ": hello"), (out[0].event as SystemMessage).text.spans.map { it.text })
    }

    @Test
    fun `a name another capture beside it shows, where no rule finds it here, is refused`() {
        dir.resolve("other.jsonl").writeLines(listOf(captureLine(0, say("Erin has obtained a thing"))))
        val capture = capture(1 to tab(), 2 to say("Thanks Erin for the carry"))
        val refused = runCatching { cut(capture, 0.0..10.0, setOf("system")) }.exceptionOrNull()
        assertTrue(refused is IllegalStateException && "Erin" in refused.message.orEmpty(), "$refused")
        assertFalse(dir.resolve("fixture.jsonl").toFile().exists())
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
        val events = cutRead(capture, 7.0..9.0, setOf("system", "tablist", "container"))
        assertKinds(events, TabList::class, ContainerOpened::class, SlotChanged::class, SystemMessage::class)
        assertEquals(2, (events[2].event as SlotChanged).slot)
        assertEquals("Area: The End", (events[0].event as TabList).rows.last().plain)
        assertEquals(7000L, events[0].time)
    }

    @Test
    fun `a return to the inventory that keeps its slots is written with every slot it kept`() {
        val stone = Item("minecraft:stone", 1)
        val dirt = Item("minecraft:dirt", 2)
        val capture = capture(
            1 to ContainerOpened(0, null, 46),
            2 to SlotChanged(9, stone),
            3 to SlotChanged(10, dirt),
            4 to ContainerOpened(5, text("Shop"), 63),
            5 to SlotChanged(0, Item("minecraft:emerald", 1)),
            6 to ContainerOpened(0, null, 46, kept = true),
            7 to SlotChanged(10, null),
            8 to ContainerOpened(5, text("Shop"), 63),
            9 to ContainerOpened(0, null, 46, kept = true),
        )
        fun events(seconds: ClosedFloatingPointRange<Double>) =
            cutRead(capture, seconds, setOf("container")).map { it.event }

        assertEquals(
            listOf(ContainerOpened(5, text("Shop"), 63), SlotChanged(0, Item("minecraft:emerald", 1)), ContainerOpened(0, null, 46), SlotChanged(9, stone), SlotChanged(10, dirt)),
            events(5.5..6.5),
        )
        assertEquals(listOf(ContainerOpened(0, null, 46), SlotChanged(9, stone)), events(8.5..9.5).filter { it !is ContainerOpened || it.id == 0 }.takeLast(2))
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
        val out = cutRead(capture, 1.5..9.0, setOf("tablist"), listOf("Area"))
        assertEquals(listOf("Area: The End", "Area: Hub"), out.map { (it.event as TabList).rows.first { row -> row.plain.startsWith("Area") }.plain })
    }

    @Test
    fun `the rows that tell the feature's area are kept with no flag, at the start and at each change`() {
        val capture = capture(
            1 to tab("Area: The End", "Dragon: 100%"),
            2 to Sidebar(lines = listOf(text("⏣ Dragon's Nest"))),
            3 to tab("Area: The End", "Dragon: 90%"),
            4 to Sidebar(lines = listOf(text("⏣ Void Sepulture"), text("Purse: 5"))),
            5 to Sidebar(lines = listOf(text("⏣ Void Sepulture"), text("Purse: 6"))),
            6 to tab("Area: Hub"),
            7 to say("inside"),
        )
        val out = cutRead(capture, 2.5..9.0, setOf("system"), area = Area.VOID_SEPULTURE)
        val shown = out.map { (time, event) ->
            time to when (event) {
                is TabList -> event.rows.first { it.plain.startsWith("Area") }.plain
                is Sidebar -> event.lines.first().plain
                else -> (event as SystemMessage).text.plain
            }
        }
        assertEquals(
            listOf(2500L to "Area: The End", 2500L to "⏣ Dragon's Nest", 4000L to "⏣ Void Sepulture", 6000L to "Area: Hub", 7000L to "inside"),
            shown,
        )
    }

    @Test
    fun `a change of world is kept with no flag, and the area's rows are written again after it`() {
        val capture = capture(
            1 to tab("Area: The End"),
            2 to WorldChanged,
            3 to tab("Area: The End"),
            4 to say("inside"),
            5 to WorldChanged,
            7 to tab("Area: The End"),
        )
        val out = cutRead(capture, 0.0..4.5, setOf("system"), area = Area.END)
        assertKinds(out, TabList::class, WorldChanged::class, TabList::class, SystemMessage::class)
        val hop = cutRead(capture, 3.5..6.0, setOf("system"), area = Area.END)
        assertKinds(hop, TabList::class, SystemMessage::class, WorldChanged::class)
        val late = cutRead(capture, 6.0..9.0, setOf("system"), area = Area.END)
        assertKinds(late, TabList::class)
        assertEquals(7000L, late.single().time)
    }

    @Test
    fun `a cut keeps the client tick on each event it keeps`() {
        val capture = dir.resolve("capture.jsonl").also { file ->
            file.writeLines(
                listOf(
                    captureLine(0, Sidebar(lines = listOf(text("Name: Carol"))), tick = 1),
                    captureLine(1_000, say("before"), tick = 2),
                    captureLine(2_000, say("inside"), tick = 5),
                    captureLine(2_000, say("same tick"), tick = 5),
                    captureLine(3_000, say("next"), tick = 8),
                ),
            )
        }
        val events = cutRead(capture, 2.0..4.0, setOf("system"))
        assertEquals(listOf(5, 5, 8), events.map { it.tick })
        assertEquals(listOf("inside", "same tick", "next"), events.map { (it.event as SystemMessage).text.plain })
    }

    @Test
    fun `an older capture's text displays become floating text, once per new text`() {
        fun entities(second: Int, full: Boolean, vararg entries: Pair<String, String?>) = buildJsonObject {
            put("t", second * 1000L)
            put("type", "entities")
            put("data", buildJsonObject {
                put("full", full)
                put("entries", buildJsonObject {
                    for ((id, shown) in entries) {
                        if (shown == null) put(id, JsonNull) else put(id, buildJsonObject {
                            put("entity", if (id == "pig") "minecraft:pig" else "minecraft:text_display")
                            put("text", buildJsonObject { put("text", shown) })
                        })
                    }
                })
            })
        }.toString()
        val capture = dir.resolve("legacy.jsonl").also {
            it.writeLines(
                listOf(
                    sidebar,
                    entities(1, true, "1" to "§f✧§e1§c2§f✧", "pig" to "Pig"),
                    entities(2, false, "1" to "§f✧§e1§c2§f✧", "2" to "§798,438"),
                    entities(3, false, "1" to null, "2" to "§7Done"),
                    entities(4, false, "1" to "§f✧§e1§c2§f✧"),
                ),
            )
        }
        val out = cutRead(capture, 0.0..10.0, setOf("floating")).map { it.event as FloatingText }
        assertEquals(listOf("✧12✧", "98,438", "Done", "✧12✧"), out.map { it.text.plain })
        assertEquals(listOf(0xFFFFFF, 0xFFFF55, 0xFF5555, 0xFFFFFF), out[0].text.spans.map { it.color })
        assertEquals(listOf(0xAAAAAA), out[1].text.spans.map { it.color })
    }
}

