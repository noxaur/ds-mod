package io.github.noxaur.dsmod.debug.capture

import io.github.noxaur.dsmod.core.capture.PlayerNames
import io.github.noxaur.dsmod.core.capture.Redactor
import io.github.noxaur.dsmod.core.capture.captureLine
import io.github.noxaur.dsmod.core.capture.readCaptureLine
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.TabState
import io.github.noxaur.dsmod.core.game.WorldChanged
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.useLines

/**
 * Cuts the events of one window out of [capture] into [fixture], redacted. [types] are the event types to keep,
 * and `container` keeps `slot` too. The fixture begins with the state at the window's start: the last tab list
 * and sidebar before it and the open container. A tab list inside the window is kept only when one of the [info] rows
 * changed, and every one is kept when there are none. A system line is kept when it matches [system], or always without one.
 * Returns the number of lines written and of players renamed.
 */
fun cutFixture(
    capture: Path,
    fixture: Path,
    seconds: ClosedFloatingPointRange<Double>,
    types: Set<String>,
    info: List<String> = emptyList(),
    system: Regex? = null,
): Pair<Int, Int> {
    val kept = if ("container" in types) types + "slot" else types
    val redactor = Redactor(PlayerNames.of(capture))
    var origin = -1L
    var tab: TabList? = null
    var sidebar: Sidebar? = null
    var shown: List<String?>? = null
    var container: ContainerOpened? = null
    val slots = sortedMapOf<Int, Item>()
    val events = ArrayList<Pair<Long, GameEvent>>()
    capture.useLines { lines ->
        for (line in lines.filter(String::isNotBlank)) {
            val time = line.substringAfter("\"t\":").takeWhile(Char::isDigit).toLong()
            if (origin < 0) origin = time
            if (line.substringAfter("\"type\":\"").substringBefore('"') !in kept) continue
            val event = readCaptureLine(line)!!.second
            val second = (time - origin) / 1000.0
            if (second > seconds.endInclusive) break
            val before = second < seconds.start
            when {
                event is TabList -> {
                    val state = TabState().apply { update(event.rows) }
                    val values = info.map(state::info)
                    if (before) tab = event else if (info.isEmpty() || values != shown) events += time to event
                    shown = values
                }
                event is Sidebar && before -> sidebar = event
                event is ContainerOpened && before -> {
                    container = event
                    slots.clear()
                }
                event is SlotChanged && before -> if (event.item == null) slots.remove(event.slot) else slots[event.slot] = event.item!!
                before -> Unit
                event !is SystemMessage || system == null || system.containsMatchIn(event.text.plain) -> events += time to event
            }
        }
    }
    val start = origin + (seconds.start * 1000).toLong()
    val state = listOfNotNull(tab, sidebar, container) + slots.map { (slot, item) -> SlotChanged(slot, item) }
    val lines = (state.map { start to it } + events).map { (time, event) -> captureLine(time, redactor.redact(event)) }
    Files.createDirectories(fixture.parent)
    Files.write(fixture, lines)
    return lines.size to redactor.count
}

/**
 * `cutFixture <capture> <feature id> <name> <from>-<to> [--types a,b] [--info Key,Key] [--system regex]`
 * writes `src/test/resources/features/<feature id>/<name>.jsonl`. The window is in seconds from the capture's
 * first line, and `--types` defaults to `system,tablist`.
 */
fun main(args: Array<String>) {
    val (capture, feature, name, window) = args
    val options = args.drop(4).chunked(2).associate { (flag, value) -> flag to value }
    val (from, to) = window.split('-').map(String::toDouble)
    val (written, players) = cutFixture(
        Path.of(capture),
        Path.of("src/test/resources/features/$feature/$name.jsonl"),
        from..to,
        (options["--types"] ?: "system,tablist").split(',').toSet(),
        options["--info"]?.split(',').orEmpty(),
        options["--system"]?.let(::Regex),
    )
    println("Wrote $written lines for $feature/$name, with $players players renamed.")
}
