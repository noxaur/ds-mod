package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.harness.encode
import io.github.noxaur.dsmod.core.capture.Captured
import io.github.noxaur.dsmod.harness.readCapture
import io.github.noxaur.dsmod.harness.chat
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.harness.text
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.BlockBroken
import io.github.noxaur.dsmod.core.game.Breaker
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.Hit
import io.github.noxaur.dsmod.core.game.EntityGone
import io.github.noxaur.dsmod.core.game.EntityRenamed
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.FrameRate
import io.github.noxaur.dsmod.core.game.PingMeasured
import io.github.noxaur.dsmod.core.game.ServerTime
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SlotClicked
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.EntityBeam
import io.github.noxaur.dsmod.core.world.Tracer
import io.github.noxaur.dsmod.core.world.Waypoint
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class FeatureReplayTest {
    private val root = Path.of("src/test/resources/features")

    private fun play(
        game: Game,
        events: List<Captured>,
        posted: (time: Long, event: GameEvent, kept: Boolean) -> Unit = { _, _, _ -> },
        ticked: (time: Long) -> Unit = {},
    ) {
        if (events.none { it.tick != null }) {
            for ((time, event) in events) {
                posted(time, event, game.post(time, event))
                game.tick(time)
                ticked(time)
            }
            return
        }
        var index = 0
        while (index < events.size) {
            val tick = events[index].tick
            var time = events[index].time
            while (index < events.size && events[index].tick == tick) {
                val captured = events[index]
                posted(captured.time, captured.event, game.post(captured.time, captured.event))
                time = captured.time
                index++
            }
            game.tick(time)
            ticked(time)
        }
    }

    private fun ciTree() = SettingsTree(mac = false)

    private val owners = ciTree().owners.associate { it.feature.id to it::class }

    private fun fresh(id: String) = object : Harness(alone = owners.getValue(id)) {}.also { it.readout().feature.enabled = true }

    private fun eachFixture(feature: Feature, body: (fixture: Path, session: Harness) -> Unit) {
        for (fixture in fixtures(feature)) body(fixture, fresh(feature.id))
    }

    private fun fixtures(feature: Feature): List<Path> {
        val dir = root.resolve(feature.id)
        val fixtures = if (dir.isDirectory()) dir.listDirectoryEntries("*.jsonl").sorted() else emptyList()
        if (fixtures.isNotEmpty()) return fixtures
        assertTrue(
            feature.handlers.isEmpty() && !feature.hasHud,
            "${feature.id} has no fixture. Put an excerpt of a capture at $dir/<name>.jsonl.",
        )
        val owner = ciTree().owners.single { it.feature.id == feature.id }
        val gameTest = Path.of("src/gametest/kotlin/io/github/noxaur/dsmod/gametest/${owner.javaClass.simpleName}Test.kt")
        assertTrue(gameTest.exists(), "${feature.id} reads nothing from the server, so it needs a game test at $gameTest.")
        return fixtures
    }

    private fun events(feature: Feature, fixture: Path): List<Captured> {
        val events = readCapture(fixture)
        val start = events.first().time
        val row = feature.area.key?.let { "$it: ${feature.area.value}" }?.takeIf { events.none { it.event is TabList } }
        val line = feature.area.place?.let { "⏣ $it" }?.takeIf { events.none { it.event is Sidebar } }
        return listOfNotNull(row?.let { Captured(start, TabList(lines(it))) }, line?.let { Captured(start, Sidebar(lines = lines(it))) }) + events
    }

    private fun name(item: Item) = item.name?.plain?.ifBlank { null } ?: item.id

    private fun describe(event: GameEvent, previous: MutableSet<String>) = when (event) {
        is SystemMessage -> event.text.plain
        WorldChanged -> "(the world changed)"
        LeftServer -> "(left the server)"
        is TabList -> {
            val info = event.rows.map { it.plain.trim() }.filter { ": " in it }.toSet()
            val changed = (info - previous).ifEmpty { setOf("no info row changed") }
            previous.clear()
            previous += info
            "(tab list) " + changed.joinToString("; ")
        }
        is Sidebar -> "(sidebar) " + event.lines.map { it.plain.trim() }.filter { it.isNotEmpty() }.joinToString("; ")
        is ContainerOpened -> "(opened ${event.title?.let { "\"${it.plain}\"" } ?: "your inventory"}, ${event.size} slots)"
        is HeadPlaced -> "(head placed) at ${event.x}, ${event.y}, ${event.z}"
        is ActionBar -> "(action bar) ${event.text.plain}"
        is EntityDied -> "(died) ${event.name.plain}"
        is EntitySeen -> "(seen) ${event.id} ${event.entity} ${event.name.plain}" + (event.head?.let { " wearing ${name(it)}" } ?: "")
        is EntityRenamed -> "(renamed) ${event.id} ${event.name.plain}"
        is EntityGone -> "(gone) ${event.id}"
        is FloatingText -> "(floating) ${event.text.plain.replace("\n", " / ")}"
        is Hit -> "(you hit) ${event.entity}"
        is ServerTime -> "(server time) tick ${event.gameTime}"
        is PingMeasured -> "(ping) ${event.millis} ms"
        is FrameRate -> "(frame rate) ${event.fps} fps"
        is SlotChanged -> "(slot ${event.slot}) " + (event.item?.let { "${it.count} x ${name(it)}" } ?: "emptied")
        is SlotClicked -> "(you clicked slot ${event.slot})"
        is BlockBroken -> "(broken) ${event.block} at ${event.x}, ${event.y}, ${event.z} " + when (event.by) {
            Breaker.PLAYER -> "by you"
            Breaker.WITH_PLAYER -> "with your break"
            Breaker.OTHER -> "by someone else"
        }
    }

    @TestFactory
    fun `every feature shows what its transcript says`(): List<DynamicTest> = ciTree().features.map { feature ->
        dynamicTest(feature.id) {
            val written = ArrayList<Path>()
            eachFixture(feature) { fixture, session ->
                val game = session.game
                val events = events(feature, fixture)
                val start = events.first().time
                var shown = emptyList<String>()
                var marked = emptyMap<Int, SlotMark>()
                var drawn = emptyList<Mark>()
                val infoRows = HashSet<String>()
                val transcript = buildString {
                    fun readout() {
                        val lines = session.readout().hud?.let { block -> listOfNotNull(block.header.plain.ifEmpty { null }, block.icon?.let { "  (icon) ${name(it)}" }) + block.rows.map { "  " + it.text.plain } }
                            ?: emptyList()
                        if (lines != shown) {
                            shown = lines
                            lines.ifEmpty { listOf("(nothing)") }.forEach { appendLine("             $it") }
                        }
                        val marks = session.readout().slots
                        if (marks != marked) {
                            for (slot in marked.keys.sorted()) if (marks[slot] != marked[slot]) appendLine("             - slot $slot")
                            for (slot in marks.keys.sorted()) {
                                val mark = marks.getValue(slot)
                                if (mark == marked[slot]) continue
                                val item = name(game.container.slots[slot]!!)
                                if (mark.hide) {
                                    appendLine("             + slot $slot: hidden, on $item")
                                    continue
                                }
                                val cooldown = mark.cooldown
                                if (cooldown != null) {
                                    appendLine("             + slot $slot: cooldown until +%.1fs on %s".format(Locale.ROOT, (cooldown.end - start) / 1000.0, item))
                                    continue
                                }
                                val shape = if (mark.rarity) "rarity" else if (mark.outline) "outline" else "fill"
                                val label = mark.label?.let { " \"${it.plain}\"" } ?: ""
                                appendLine("             + slot $slot: ${mark.role} $shape$label on $item")
                            }
                            marked = marks
                        }
                        val inWorld = session.readout().marks
                        if (inWorld != drawn) {
                            drawn = inWorld
                            if (inWorld.isEmpty()) appendLine("             (no marks in the world)")
                            for (mark in inWorld) {
                                appendLine(
                                    "             (in the world) " + when (mark) {
                                        is Waypoint -> "${mark.role} beam \"${mark.label}\" at ${mark.at.x}, ${mark.at.y}, ${mark.at.z}"
                                        is EntityBeam -> "${mark.role} beam \"${mark.label}\" on entity ${mark.entity}"
                                        is Tracer -> "${mark.role} tracer to entity ${mark.entity}"
                                        else -> mark.toString()
                                    },
                                )
                            }
                        }
                    }
                    play(game, events, posted = { time, event, kept ->
                        appendLine("+%6.1fs > %s".format(Locale.ROOT, (time - start) / 1000.0, describe(event, infoRows)))
                        if (!kept) appendLine(if (event is FloatingText) "             (hidden)" else "             (hidden from chat)")
                        game.replaced?.let { appendLine("             (shown as) ${it.plain}") }
                        for (message in game.messages) {
                            when (message) {
                                is Notice -> appendLine("             (chat) ${message.feature} | ${message.detail.plain}")
                                is Alert -> appendLine("             (title) ${message.title.plain}, (chat) ${message.feature} | ${message.detail.plain}")
                            }
                        }
                        game.messages.clear()
                    }, ticked = { readout() })
                    val metrics = game.metrics.snapshot()
                    assertEquals(emptyMap(), metrics.failures, "${feature.id} threw while replaying $fixture")
                    metrics.counters.toSortedMap().forEach { (name, count) -> appendLine("$name = $count") }
                    session.readout().feature.state?.let { appendLine((if (it.profiled) "saved for the profile ${feature.id}" else "saved ${feature.id}.json") + " = ${it.encode()}") }
                    if (game.hidden.value.told.isNotEmpty()) appendLine("saved hidden.json = ${game.hidden.encode()}")
                }

                val expected = fixture.resolveSibling(fixture.nameWithoutExtension + ".txt")
                if (!expected.exists()) {
                    expected.writeText(transcript)
                    written.add(expected)
                    return@eachFixture
                }
                assertEquals(
                    expected.readText(),
                    transcript,
                    "${feature.id} no longer matches $expected. If the new readout is right, delete that file and run again.",
                )
            }
            if (written.isNotEmpty()) fail("Wrote $written. Read each one, and keep it only if it shows what the player should see.")
        }
    }

    @TestFactory
    fun `a feature locked to an area does nothing outside it`(): List<DynamicTest> =
        ciTree().features.filter { it.area != Area.ANYWHERE }.map { feature ->
            dynamicTest(feature.id) {
                val untouched = feature.state?.encode()
                eachFixture(feature) { fixture, session ->
                    val game = session.game
                    val state = session.readout().feature.state
                    game.post(0, TabList(lines("Area: Somewhere Else")))
                    game.post(0, Sidebar(lines = lines("⏣ Somewhere Else")))
                    val outside = readCapture(fixture).filter { it.event !is TabList && it.event !is Sidebar }
                    play(game, outside, posted = { time, event, kept ->
                        val where = "outside ${feature.area}, replaying $fixture, after $event at $time"
                        assertTrue(kept, "${feature.id} hid a line $where")
                        assertEquals(null, game.replaced, "${feature.id} replaced a text $where")
                        assertEquals(emptyList(), game.messages, "${feature.id} spoke $where")
                    }, ticked = {
                        val where = "outside ${feature.area}, replaying $fixture"
                        assertEquals(null, session.readout().hud, "${feature.id} showed its HUD $where")
                        assertEquals(emptyMap(), session.readout().slots, "${feature.id} marked slots $where")
                        assertEquals(untouched, state?.encode(), "${feature.id} saved something $where")
                    })
                }
            }
        }

    @Test
    fun `play ticks once for the events of one recorded client tick`() {
        val game = fresh(ciTree().features.first().id).game
        val death = chat("Zealot was slain")
        val bar = ActionBar(text("Kills: 4"))
        val ticks = ArrayList<Long>()
        play(
            game,
            listOf(
                Captured(1794683683200, death, tick = 4),
                Captured(1794683683200, bar, tick = 4),
                Captured(1794683683201, death, tick = 5),
            ),
            ticked = { ticks += it },
        )
        assertEquals(listOf(1794683683200, 1794683683201), ticks)
    }

    @Test
    fun `play ticks after every event when the capture recorded no client tick`() {
        val game = fresh(ciTree().features.first().id).game
        val line = chat("hello")
        val ticks = ArrayList<Long>()
        val events = listOf(Captured(100, line), Captured(101, line), Captured(149, line))
        play(game, events, ticked = { ticks += it })
        assertEquals(events.map { it.time }, ticks)
    }
}
