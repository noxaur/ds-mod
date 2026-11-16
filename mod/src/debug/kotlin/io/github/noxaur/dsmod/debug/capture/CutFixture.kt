package io.github.noxaur.dsmod.debug.capture

import io.github.noxaur.dsmod.core.capture.Captured
import io.github.noxaur.dsmod.core.capture.PlayerNames
import io.github.noxaur.dsmod.core.capture.Redactor
import io.github.noxaur.dsmod.core.capture.captureLine
import io.github.noxaur.dsmod.core.capture.readCaptureLine
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SidebarState
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.TabState
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.edge.toText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.useLines

/**
 * Cuts the events of one window out of [capture] into [fixture], redacted. [types] are the event types to keep,
 * and `container` keeps `slot` too. The tab list and the sidebar that tell [area], and each change of world, are kept
 * whatever [types] says, because a fixture without them replays as if the player never left. The fixture begins with the state at the
 * window's start: the last tab list and sidebar before it and the open container. A return to the player's inventory
 * that keeps its slots is written with every slot it kept, so a fixture never needs what came before it. A tab list
 * inside the window is kept only when one of the [info] rows or the area's row changed, and every one is kept when
 * `tablist` is a type and there are no [info] rows. A sidebar inside the window is kept when `sidebar` is a type, and
 * otherwise only when its place changed. A system line is kept when it matches [system], or always without one.
 * Returns the number of lines written and of players renamed.
 */
fun cutFixture(
    capture: Path,
    fixture: Path,
    seconds: ClosedFloatingPointRange<Double>,
    types: Set<String>,
    area: Area,
    info: List<String> = emptyList(),
    system: Regex? = null,
): Pair<Int, Int> {
    val kept = types + buildSet {
        if ("container" in types) add("slot")
        if (area != Area.ANYWHERE) add("world")
        if (area.key != null) add("tablist")
        if (area.place != null) add("sidebar")
    }
    val rows = if ("tablist" in types) info + listOfNotNull(area.key) else listOfNotNull(area.key)
    val everyTab = "tablist" in types && info.isEmpty()
    val redactor = Redactor(PlayerNames.of(capture))
    var origin = -1L
    var tab: TabList? = null
    var sidebar: Sidebar? = null
    var shown: List<String?>? = null
    var place: String? = null
    // The open container and its slots, the player's own inventory as it was when another menu opened, and the
    // container and slots as the window starts.
    var open: ContainerOpened? = null
    val slots = sortedMapOf<Int, Item>()
    var inventory = emptyMap<Int, Item>()
    var container: ContainerOpened? = null
    var start: Map<Int, Item>? = null
    val events = ArrayList<Captured>()
    // A capture from before `floating` was an event has its text displays only in `entities`.
    val legacy = if ("floating" in types && capture.useLines { lines -> lines.none { "\"type\":\"floating\"" in it } }) HashMap<String, String>() else null
    capture.useLines { lines ->
        for (line in lines.filter(String::isNotBlank)) {
            val time = line.substringAfter("\"t\":").takeWhile(Char::isDigit).toLong()
            if (origin < 0) origin = time
            val type = line.substringAfter("\"type\":\"").substringBefore('"')
            val read = when {
                type == "entities" && legacy != null -> floating(line, legacy)
                type in kept -> listOf(readCaptureLine(line)!!)
                else -> continue
            }
            val second = (time - origin) / 1000.0
            if (second > seconds.endInclusive) break
            for (captured in read) {
                val event = captured.event
                val tick = captured.tick
                val before = second < seconds.start
                if (!before && start == null) {
                    container = open?.copy(kept = false)
                    start = slots.toMap()
                }
                if (event is ContainerOpened) {
                    if (open?.id == INVENTORY) inventory = slots.toMap()
                    open = event
                    slots.clear()
                    if (event.kept) slots.putAll(inventory)
                }
                if (event is SlotChanged) if (event.item == null) slots.remove(event.slot) else slots[event.slot] = event.item!!
                // `Game` forgets the tab list and the sidebar with the world, so the next world's rows must be written again.
                if (event == WorldChanged) {
                    if (before) {
                        tab = null
                        sidebar = null
                    }
                    shown = null
                    place = null
                }
                when {
                    event is TabList -> {
                        val state = TabState().apply { update(event.rows) }
                        val values = rows.map(state::info)
                        if (before) tab = event else if (everyTab || values != shown) events += Captured(time, event, tick)
                        shown = values
                    }
                    event is Sidebar -> {
                        val now = SidebarState().apply { update(event) }.place
                        if (before) sidebar = event else if ("sidebar" in types || now != place) events += Captured(time, event, tick)
                        place = now
                    }
                    event is ContainerOpened && !before -> {
                        events += Captured(time, event.copy(kept = false), tick)
                        if (event.kept) slots.forEach { (slot, item) -> events += Captured(time, SlotChanged(slot, item), tick) }
                    }
                    before -> Unit
                    event !is SystemMessage || system == null || system.containsMatchIn(event.text.plain) -> events += Captured(time, event, tick)
                }
            }
        }
    }
    if (start == null) {
        container = open?.copy(kept = false)
        start = slots.toMap()
    }
    val state = listOfNotNull(tab, sidebar, container) + start!!.map { (slot, item) -> SlotChanged(slot, item) }
    // The carried state was posted on earlier ticks the window does not keep, so it has no tick of its own.
    val lines = (state.map { Captured(origin + (seconds.start * 1000).toLong(), it) } + events)
        .map { captureLine(it.time, redactor.redact(it.event), it.tick) }
    Files.createDirectories(fixture.parent)
    Files.write(fixture, lines)
    return lines.size to redactor.count
}

/**
 * `cutFixture <capture> <feature id> <name> <from>-<to> [--types a,b] [--info Key,Key] [--system regex]`
 * writes `src/test/resources/features/<feature id>/<name>.jsonl`, keeping the rows that tell the feature's area. The
 * window is in seconds from the capture's first line, and `--types` defaults to `system,tablist`.
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
        features(DsModConfig()).singleOrNull { it.id == feature }?.area ?: error("No feature has the id $feature."),
        options["--info"]?.split(',').orEmpty(),
        options["--system"]?.let(::Regex),
    )
    println("Wrote $written lines for $feature/$name, with $players players renamed.")
}

/**
 * The [FloatingText] of each text display in an older capture's `entities` line whose text is new: the server's
 * text, read the way the edge reads it, and [shown] is each display's text so far. The line is the recorder's sample,
 * so the text arrives up to a few ticks after the client had it.
 */
private fun floating(line: String, shown: MutableMap<String, String>): List<Captured> {
    val json = Json.parseToJsonElement(line).jsonObject
    val time = json.getValue("t").jsonPrimitive.long
    val tick = json["tick"]?.jsonPrimitive?.int
    return json.getValue("data").jsonObject.getValue("entries").jsonObject.mapNotNull { (id, entry) ->
        if (entry !is JsonObject) return@mapNotNull null.also { shown.remove(id) }
        if (entry["entity"]?.jsonPrimitive?.content != "minecraft:text_display") return@mapNotNull null
        val text = entry["text"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
        if (text.isEmpty() || shown.put(id, text) == text) return@mapNotNull null
        Captured(time, FloatingText(Component.literal(text).toText()), tick)
    }
}

/** The player's own inventory, which the client always numbers 0. */
private const val INVENTORY = 0
