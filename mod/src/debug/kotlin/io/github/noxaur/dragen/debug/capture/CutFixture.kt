package io.github.noxaur.dragen.debug.capture

import io.github.noxaur.dragen.core.capture.Captured
import io.github.noxaur.dragen.core.capture.PlayerNames
import io.github.noxaur.dragen.core.capture.Redactor
import io.github.noxaur.dragen.core.capture.captureLine
import io.github.noxaur.dragen.core.capture.rawCaptureLine
import io.github.noxaur.dragen.core.capture.readCaptureLine
import io.github.noxaur.dragen.core.config.SettingsTree
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.core.game.ContainerState.Companion.INVENTORY
import io.github.noxaur.dragen.core.game.EntityFilter
import io.github.noxaur.dragen.core.game.EntityTable
import io.github.noxaur.dragen.core.game.GameEvent
import io.github.noxaur.dragen.core.game.FloatingText
import io.github.noxaur.dragen.core.game.HeadPlaced
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.LeftServer
import io.github.noxaur.dragen.core.game.Sidebar
import io.github.noxaur.dragen.core.game.SidebarState
import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.core.game.SystemMessage
import io.github.noxaur.dragen.core.game.TabList
import io.github.noxaur.dragen.core.game.TabState
import io.github.noxaur.dragen.core.game.WorldChanged
import io.github.noxaur.dragen.core.game.view
import io.github.noxaur.dragen.edge.toText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.useLines

fun cutFixture(
    capture: Path,
    fixture: Path,
    seconds: ClosedFloatingPointRange<Double>,
    types: Set<String>,
    area: Area,
    info: List<String> = emptyList(),
    system: Regex? = null,
    entities: List<EntityFilter> = emptyList(),
    raw: (type: String, data: JsonObject) -> JsonObject? = { _, _ -> null },
): Pair<Int, Int> {
    val kept = types + buildSet {
        if ("container" in types) add("slot")
        if (area != Area.ANYWHERE) add("world")
        if (area.key != null) add("tablist")
        if (area.place != null) add("sidebar")
        if (entities.isNotEmpty()) addAll(listOf("seen", "renamed", "gone", "world", "left"))
    }
    val rows = if ("tablist" in types) info + listOfNotNull(area.key) else listOfNotNull(area.key)
    val everyTab = "tablist" in types && info.isEmpty()
    val redactor = Redactor(PlayerNames.of(capture))
    var origin = -1L
    var tab: TabList? = null
    var sidebar: Sidebar? = null
    var shown: List<String?>? = null
    var place: String? = null
    var open: ContainerOpened? = null
    val slots = sortedMapOf<Int, Item>()
    var inventory = emptyMap<Int, Item>()
    var container: ContainerOpened? = null
    var start: Map<Int, Item>? = null
    val held = EntityTable()
    var heldAtStart = emptyList<GameEvent>()
    fun begin() {
        container = open?.copy(kept = false)
        start = slots.toMap()
        heldAtStart = held.values.filter { seen -> entities.any { it.matches(seen) } }
    }
    val events = ArrayList<Captured>()
    val rawLines = ArrayList<Pair<Int, String>>()
    val legacy = if ("floating" in types && capture.useLines { lines -> lines.none { "\"type\":\"floating\"" in it } }) HashMap<String, String>() else null
    capture.useLines { lines ->
        for (line in lines.filter(String::isNotBlank)) {
            val time = line.substringAfter("\"t\":").takeWhile(Char::isDigit).toLong()
            if (origin < 0) origin = time
            val type = line.substringAfter("\"type\":\"").substringBefore('"')
            val second = (time - origin) / 1000.0
            if (second > seconds.endInclusive) break
            val read = when {
                type == "entities" && legacy != null -> floating(line, legacy)
                type in kept -> listOf(checkNotNull(readCaptureLine(line)) { "A line of a kept type did not read: $line" })
                else -> {
                    if (second < seconds.start) continue
                    val json = Json.parseToJsonElement(line).jsonObject
                    val data = raw(type, json.getValue("data").jsonObject) ?: continue
                    if (start == null) begin()
                    rawLines += events.size to rawCaptureLine(time, type, data, json["tick"]?.jsonPrimitive?.int)
                    continue
                }
            }
            for (captured in read) {
                val event = captured.event
                val tick = captured.tick
                val before = second < seconds.start
                if (!before && start == null) begin()
                if (event is ContainerOpened) {
                    if (open?.id == INVENTORY) inventory = slots.toMap()
                    open = event
                    slots.clear()
                    if (event.kept) slots.putAll(inventory)
                }
                if (event is SlotChanged) event.item.let { item -> if (item == null) slots.remove(event.slot) else slots[event.slot] = item }
                if (held.apply(event)) {
                    if (!before) entities.view(held.before, held.after, event)?.let { events += Captured(time, it, tick) }
                    continue
                }
                if (event == WorldChanged || event == LeftServer) held.clear()
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
    if (start == null) begin()
    val state = listOfNotNull(tab, sidebar, container) + checkNotNull(start) { "begin sets the slots the window starts with" }.map { (slot, item) -> SlotChanged(slot, item) } + heldAtStart
    val opening = state.map { captureLine(origin + (seconds.start * 1000).toLong(), redactor.redact(it)) }
    val raws = rawLines.groupBy({ it.first }, { it.second })
    val lines = opening + events.flatMapIndexed { index, it -> raws[index].orEmpty() + captureLine(it.time, redactor.redact(it.event), it.tick) } + raws[events.size].orEmpty()
    val others = capture.toAbsolutePath().parent.listDirectoryEntries("*.jsonl")
    val leaked = PlayerNames.leaked(PlayerNames.of(capture, *others.toTypedArray()), lines.joinToString("\n"))
    check(leaked.isEmpty()) {
        "The fixture would hold the player names $leaked, which a capture in ${capture.parent} shows. Teach PlayerNames " +
            "where they stand from a capture that shows the line, then cut the fixture again."
    }
    Files.createDirectories(fixture.parent)
    Files.write(fixture, lines)
    return lines.size to redactor.count
}

fun main(args: Array<String>) {
    val (capture, feature, name, window) = args
    val options = args.drop(4).chunked(2).associate { (flag, value) -> flag to value }
    val (from, to) = window.split('-').map(String::toDouble)
    val types = (options["--types"] ?: "system,tablist").split(',').toSet()
    val (written, players) = if (feature == DRAGON_API) {
        cutFixture(Path.of(capture), Path.of("src/test/resources/$feature/$name.jsonl"), from..to, types + "time", Area.END, entities = DRAGON_BODY, raw = dragonRecords())
    } else {
        val found = SettingsTree().features.singleOrNull { it.id == feature } ?: error("No feature has the id $feature, and it is not $DRAGON_API.")
        cutFixture(
            Path.of(capture),
            Path.of("src/test/resources/features/$feature/$name.jsonl"),
            from..to,
            types,
            found.area,
            options["--info"]?.split(',').orEmpty(),
            options["--system"]?.let(::Regex),
            found.entityFilters,
        )
    }
    println("Wrote $written lines for $feature/$name, with $players players renamed.")
}

private const val DRAGON_API = "api/dragon"

private val DRAGON_BODY = listOf(EntityFilter(setOf("minecraft:ender_dragon")))

private val DRAGON_BLOCKS = setOf(
    "end_portal_frame", "beacon", "purple_stained_glass", "purple_stained_glass_pane", "black_concrete", "pearlescent_froglight", "purple_wool",
    "coal_block", "purple_terracotta",
).map { "minecraft:$it" }

private val DRAGON_THINGS = setOf("minecraft:ender_dragon", "minecraft:fireball", "minecraft:small_fireball", "minecraft:dragon_fireball", "minecraft:lightning_bolt")

private fun dragonRecords(): (String, JsonObject) -> JsonObject? {
    val kept = HashSet<String>()
    fun block(state: JsonElement?) = state?.jsonPrimitive?.content?.substringBefore('[') in DRAGON_BLOCKS
    return { type, data ->
        when (type) {
            "bossbars" -> data
            "block" -> data.takeIf { block(it["state"]) || block(it["was"]) }
            "motion" -> JsonObject(
                data.filter { (id, entry) ->
                    if (entry is JsonObject) (entry["entity"]?.jsonPrimitive?.content in DRAGON_THINGS).also { if (it) kept += id } else kept.remove(id)
                },
            ).takeIf { it.isNotEmpty() }
            else -> null
        }
    }
}

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
