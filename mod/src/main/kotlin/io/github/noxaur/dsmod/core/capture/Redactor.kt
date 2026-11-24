package io.github.noxaur.dsmod.core.capture

import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.BlockBroken
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.FrameRate
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.game.Hit
import io.github.noxaur.dsmod.core.game.EntityGone
import io.github.noxaur.dsmod.core.game.EntityRenamed
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.PingMeasured
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.ServerTime
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SlotClicked
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.WorldChanged
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.io.path.useLines

class Redactor(private val names: Set<String>) {
    private val aliases = HashMap<String, String>()
    private val code = Regex("§.")

    val count get() = aliases.size

    private fun alias(name: String) = aliases.getOrPut(name) { "Player${aliases.size + 1}" }

    private fun hits(text: String, here: Set<String> = emptySet(), at: List<IntRange> = emptyList()) =
        PlayerNames.word.findAll(text).filter { it.value in names || it.value in here || it.range in at }.map { it.range to alias(it.value) }.toList()

    /** @keep An older capture's raw text carries color codes, which can stand right before a name or between its letters. */
    private fun names(text: String, here: Set<String>): String {
        val shown = text.replace(code, "")
        val hits = hits(shown, here, listOfNotNull(PlayerNames.inLore(shown))).ifEmpty { return text }
        return buildString {
            var at = 0
            for ((range, alias) in hits) {
                append(shown, at, range.first).append(alias)
                at = range.last + 1
            }
            append(shown, at, shown.length)
        }
    }

    private fun ServerText.replacing(hits: List<Pair<IntRange, String>>): ServerText {
        if (hits.isEmpty()) return this
        var at = 0
        var next = 0
        return ServerText(
            spans.mapNotNull { span ->
                val text = buildString {
                    for (char in span.text) {
                        while (next < hits.size && hits[next].first.last < at) next++
                        val hit = hits.getOrNull(next)?.takeIf { at in it.first }
                        if (hit == null) append(char) else if (at == hit.first.first) append(hit.second)
                        at++
                    }
                }
                if (text.isEmpty() && span.text.isNotEmpty()) null else span.copy(text = text)
            },
        )
    }

    private fun ServerText.named(at: List<IntRange> = emptyList()) = replacing(hits(plain, at = at))

    private fun typed(text: ServerText): ServerText {
        val chat = PlayerNames.typed(text.plain)
        val at = PlayerNames.inText(text.plain) + chat.flatMap { it.names }
        val typed = chat.map { it.words to "hello" }
        val named = hits(text.plain, at = at).filter { (range) -> typed.none { range.first in it.first } }
        return text.replacing((typed + named).sortedBy { it.first.first })
    }

    private fun shown(component: JsonElement?): String = when (component) {
        is JsonObject -> shown(component["text"] as? JsonPrimitive) + shown(component["extra"] as? JsonArray)
        is JsonArray -> component.joinToString("") { shown(it) }
        is JsonPrimitive -> if (component.isString) component.content else ""
        null -> ""
    }

    private fun scrub(json: JsonElement, key: String, here: Set<String>): JsonElement = when (json) {
        is JsonObject -> {
            // @keep A profile's `id` is the player's UUID, as an int array in a skull and a string elsewhere.
            val profile = key.substringAfterLast(':') == "profile"
            val inside = here + listOfNotNull(PlayerNames.onEntity(json)?.groupValues?.get(2), if (profile) PlayerNames.inProfile(json) else null)
            val scrubbed = JsonObject(json.mapValues { (k, v) -> if (profile && k == "id") (if (v is JsonArray) JsonArray(emptyList()) else JsonPrimitive("")) else scrub(v, k, inside) })
            /** @keep A name drawn one color per letter is one piece per letter, so no single string holds it. The pieces
             * become one plain piece, which loses the line's colors and keeps the name on the player's machine. */
            val whole = if (scrubbed["extra"] is JsonArray) shown(scrubbed) else ""
            val named = names(whole, inside)
            if (named != whole) JsonPrimitive(named) else scrubbed
        }
        /** @keep An entity's `uuid`, as in a hover event, can be a player's, and an item's can be looked up on the server's
         * public profiles. Both come as a string or as an int array. */
        is JsonArray -> if (key.lowercase() == "uuid") JsonArray(emptyList()) else JsonArray(json.map { scrub(it, key, here) })
        is JsonPrimitive -> when {
            !json.isString -> json
            // @keep `value` and `signature` hold a skin.
            key.lowercase() in setOf("uuid", "signature", "value") -> JsonPrimitive("")
            else -> JsonPrimitive(names(json.content, here))
        }
    }

    /** @keep Typed text is found only in a `system` event, so a raw `chat` record, whose event says the same, is dropped, and
     * so is a `system` line of an older capture that does not read as today's event, since the scrub below keeps typed text. */
    fun line(line: String): String? {
        val json = Json.parseToJsonElement(line).jsonObject
        val type = json.getValue("type").jsonPrimitive.content
        if (type == "chat") return null
        val event = try {
            readCaptureLine(line)
        } catch (_: SerializationException) {
            if (type == "system") return null
            null
        }
        if (event != null) return captureLine(event.time, redact(event.event), event.tick)
        return JsonObject(json + ("data" to scrub(json.getValue("data"), "", emptySet()))).toString()
    }

    fun redact(event: GameEvent): GameEvent = when (event) {
        is SystemMessage -> SystemMessage(typed(event.text))
        is TabList -> TabList(event.rows.zip(PlayerNames.inTabList(event.rows.map { it.plain })) { row, at -> row.named(at) })
        is Sidebar -> Sidebar(event.title.named(), event.lines.map { it.named(listOfNotNull(PlayerNames.inSidebar(it.plain))) })
        is ContainerOpened -> event.copy(title = event.title?.named())
        is SlotChanged -> event.copy(item = event.item?.let(::item))
        WorldChanged -> event
        LeftServer -> event
        is HeadPlaced, is Hit, is ServerTime, is PingMeasured, is FrameRate, is EntityGone, is SlotClicked, is BlockBroken -> event
        is ActionBar -> ActionBar(event.text.named())
        is EntityDied -> event.copy(name = event.name.named())
        is EntitySeen -> event.copy(name = event.name.named(), head = event.head?.let(::item))
        is EntityRenamed -> event.copy(name = event.name.named())
        is FloatingText -> FloatingText(event.text.named())
    }

    private fun item(item: Item) = item.copy(
        name = item.name?.named(),
        lore = item.lore.map { it.named(listOfNotNull(PlayerNames.inLore(it.plain))) },
        data = scrub(item.data, "", emptySet()) as JsonObject,
        skin = null,
    )
}

fun redactCapture(capture: Path, to: Path, own: Set<String> = emptySet()): Int {
    val redactor = Redactor(PlayerNames.of(capture) + own)
    GZIPOutputStream(Files.newOutputStream(to)).bufferedWriter().use { out ->
        capture.useLines { lines ->
            for (line in lines.filter(String::isNotBlank)) {
                out.write(redactor.line(line) ?: continue)
                out.newLine()
            }
        }
    }
    return redactor.count
}
