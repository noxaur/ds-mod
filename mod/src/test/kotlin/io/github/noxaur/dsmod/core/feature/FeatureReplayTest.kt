package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.capture.readCapture
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Waypoint
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
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

/**
 * Replays capture excerpts through each feature. `features/<id>/<name>.jsonl` is the excerpt, and
 * `<name>.txt` beside it is the transcript: every event, whether the feature hid it, what the feature said, and
 * the HUD block, the slot marks and the marks in the world each time they changed.
 */
class FeatureReplayTest {
    private val root = Path.of("src/test/resources/features")

    /** A handler or a readout that averages more than this is too slow for the client thread. */
    private val budgetNanos = 20_000L

    private fun fresh(id: String) = Game(listOf(features(DsModConfig()).single { it.id == id }.also { it.enabled = true }))

    /** A feature that reads nothing from the server has nothing to replay, so a game test must show it instead. */
    private fun fixtures(feature: Feature): List<Path> {
        val dir = root.resolve(feature.id)
        val fixtures = if (dir.isDirectory()) dir.listDirectoryEntries("*.jsonl").sorted() else emptyList()
        if (fixtures.isNotEmpty()) return fixtures
        assertTrue(
            feature.handlers.isEmpty() && !feature.hasHud,
            "${feature.id} has no fixture. Put an excerpt of a capture at $dir/<name>.jsonl.",
        )
        val gameTest = Path.of("src/gametest/kotlin/io/github/noxaur/dsmod/gametest/${feature.javaClass.simpleName}Test.kt")
        assertTrue(gameTest.exists(), "${feature.id} reads nothing from the server, so it needs a game test at $gameTest.")
        return fixtures
    }

    private fun tabList(vararg rows: String) = TabList(rows.map { ServerText(ServerSpan(it)) })

    private fun sidebar(vararg lines: String) = Sidebar(lines = lines.map { ServerText(ServerSpan(it)) })

    /**
     * A fixture's events. One cut without the tab list or the sidebar says nothing of where the player was, so it
     * replays inside the feature's own area.
     */
    private fun events(feature: Feature, fixture: Path): List<Pair<Long, GameEvent>> {
        val events = readCapture(fixture)
        val start = events.first().first
        val row = feature.area.row?.takeIf { events.none { (_, event) -> event is TabList } }
        val line = feature.area.line?.takeIf { events.none { (_, event) -> event is Sidebar } }
        return listOfNotNull(row?.let { start to tabList(it) }, line?.let { start to sidebar(it) }) + events
    }

    private fun name(item: Item) = item.name?.plain?.ifBlank { null } ?: item.id

    /** A tab list is 40 rows, so it shows only the `Key: value` rows that differ from [previous], which it then replaces. */
    private fun describe(event: GameEvent, previous: MutableSet<String>) = when (event) {
        is SystemMessage -> event.text.plain
        WorldChanged -> "(the world changed)"
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
        is SlotChanged -> "(slot ${event.slot}) " + (event.item?.let { "${it.count} x ${name(it)}" } ?: "emptied")
    }

    @TestFactory
    fun `every feature shows what its transcript says`(): List<DynamicTest> = features(DsModConfig()).map { feature ->
        dynamicTest(feature.id) {
            val written = ArrayList<Path>()
            for (fixture in fixtures(feature)) {
                val game = fresh(feature.id)
                val events = events(feature, fixture)
                val start = events.first().first
                var shown = emptyList<String>()
                var marked = emptyMap<Int, SlotMark>()
                var drawn = emptyList<Mark>()
                val infoRows = HashSet<String>()
                val transcript = buildString {
                    for ((time, event) in events) {
                        val kept = game.post(time, event)
                        game.tick(time)
                        appendLine("+%6.1fs > %s".format(Locale.ROOT, (time - start) / 1000.0, describe(event, infoRows)))
                        if (!kept) appendLine("             (hidden from chat)")
                        for (message in game.messages) {
                            when (message) {
                                is Notice -> appendLine("             (chat) ${message.feature} | ${message.detail.plain}")
                                is Alert -> appendLine("             (title) ${message.title.plain}, (chat) ${message.feature} | ${message.detail.plain}")
                            }
                        }
                        game.messages.clear()
                        val lines = game.readouts.single().hud?.let { block -> listOf(block.header.plain) + block.rows.map { "  " + it.text.plain } }
                            ?: emptyList()
                        if (lines != shown) {
                            shown = lines
                            lines.ifEmpty { listOf("(nothing)") }.forEach { appendLine("             $it") }
                        }
                        val marks = game.readouts.single().slots
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
                                val shape = if (mark.border) "frame" else "fill"
                                val label = mark.label?.let { " \"${it.plain}\"" } ?: ""
                                appendLine("             + slot $slot: ${mark.role} $shape$label on $item")
                            }
                            marked = marks
                        }
                        val inWorld = game.readouts.single().marks
                        if (inWorld != drawn) {
                            drawn = inWorld
                            if (inWorld.isEmpty()) appendLine("             (no marks in the world)")
                            for (mark in inWorld) {
                                appendLine(
                                    "             (in the world) " + when (mark) {
                                        is Waypoint -> "${mark.role} beam \"${mark.label}\" at ${mark.at.x}, ${mark.at.y}, ${mark.at.z}"
                                        else -> mark.toString()
                                    },
                                )
                            }
                        }
                    }
                    val metrics = game.metrics.snapshot()
                    assertEquals(emptyMap(), metrics.failures, "${feature.id} threw while replaying $fixture")
                    metrics.counters.toSortedMap().forEach { (name, count) -> appendLine("$name = $count") }
                    game.readouts.single().feature.state?.let { appendLine("saved ${feature.id}.json = ${it.encode()}") }
                }

                val expected = fixture.resolveSibling(fixture.nameWithoutExtension + ".txt")
                if (!expected.exists()) {
                    expected.writeText(transcript)
                    written.add(expected)
                    continue
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

    /** The tab list and the sidebar name another place, and then the feature's own fixtures play: every line that would fire it at home. */
    @TestFactory
    fun `a feature locked to an area does nothing outside it`(): List<DynamicTest> =
        features(DsModConfig()).filter { it.area != Area.ANYWHERE }.map { feature ->
            dynamicTest(feature.id) {
                val untouched = feature.state?.encode()
                for (fixture in fixtures(feature)) {
                    val game = fresh(feature.id)
                    val state = game.readouts.single().feature.state
                    game.post(0, tabList("Area: Somewhere Else"))
                    game.post(0, sidebar("⏣ Somewhere Else"))
                    for ((time, event) in readCapture(fixture)) {
                        if (event is TabList || event is Sidebar) continue
                        assertTrue(game.post(time, event), "${feature.id} hid a line outside ${feature.area}: $event")
                        game.tick(time)
                        val where = "outside ${feature.area}, replaying $fixture, after $event"
                        assertEquals(null, game.readouts.single().hud, "${feature.id} showed its HUD $where")
                        assertEquals(emptyMap(), game.readouts.single().slots, "${feature.id} marked slots $where")
                        assertEquals(emptyList(), game.messages, "${feature.id} spoke $where")
                        assertEquals(untouched, state?.encode(), "${feature.id} saved something $where")
                    }
                }
            }
        }

    @TestFactory
    fun `every feature stays inside the time budget`(): List<DynamicTest> = features(DsModConfig()).map { feature ->
        dynamicTest(feature.id) {
            for (fixture in fixtures(feature)) {
                val game = fresh(feature.id)
                val events = events(feature, fixture)
                fun replay(times: Int) = repeat(times) {
                    for ((time, event) in events) {
                        game.post(time, event)
                        game.tick(time)
                    }
                    game.messages.clear()
                }
                // The first runs are interpreted, so they are not what a player gets.
                replay(2_000)
                game.metrics.flush()
                replay(2_000)
                game.metrics.snapshot().timers.forEach { (name, timer) ->
                    val mean = timer.totalNanos / timer.count
                    assertTrue(mean < budgetNanos, "$name takes ${mean}ns on average replaying $fixture. The budget is ${budgetNanos}ns.")
                }
            }
        }
    }
}
