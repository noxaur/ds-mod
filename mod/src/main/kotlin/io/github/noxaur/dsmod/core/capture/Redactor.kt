package io.github.noxaur.dsmod.core.capture

import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
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

/** Rewrites an event so it holds no player name, no typed message and no skin, which a public fixture must not. */
class Redactor(names: Set<String>) {
    private val real = Regex("""(?<!\w)(?:${names.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }.ifEmpty { "(?!)" }})(?!\w)""")
    private val aliases = HashMap<String, String>()

    val count get() = aliases.size

    private fun alias(name: String) = aliases.getOrPut(name) { "Player${aliases.size + 1}" }

    /** Replaces each range of the plain text, and drops what the range covered in the other spans. */
    private fun ServerText.replacing(hits: List<Pair<IntRange, String>>): ServerText {
        if (hits.isEmpty()) return this
        var at = 0
        return ServerText(
            spans.mapNotNull { span ->
                val start = at
                at += span.text.length
                val text = buildString {
                    for (i in span.text.indices) {
                        val hit = hits.firstOrNull { start + i in it.first }
                        if (hit == null) append(span.text[i]) else if (start + i == hit.first.first) append(hit.second)
                    }
                }
                if (text.isEmpty() && span.text.isNotEmpty()) null else span.copy(text = text)
            },
        )
    }

    private fun names(text: ServerText) = text.replacing(real.findAll(text.plain).map { it.range to alias(it.value) }.toList())

    private fun typed(text: ServerText): ServerText {
        val message = PlayerNames.typedFrom(text.plain, real)?.let { it until text.plain.length }?.takeUnless { it.isEmpty() }
        return names(if (message == null) text else text.replacing(listOf(message to "hello")))
    }

    /** What a text component in Minecraft's JSON shows: its own text, then each piece in `extra`. */
    private fun shown(component: JsonElement?): String = when (component) {
        is JsonObject -> shown(component["text"] as? JsonPrimitive) + shown(component["extra"] as? JsonArray)
        is JsonArray -> component.joinToString("") { shown(it) }
        is JsonPrimitive -> if (component.isString) component.content else ""
        null -> ""
    }

    private fun scrub(json: JsonElement, key: String): JsonElement = when (json) {
        is JsonObject -> {
            // A profile's `id` is the player's UUID, as an int array in a skull and a string elsewhere.
            val profile = key.substringAfterLast(':') == "profile"
            val scrubbed = JsonObject(json.mapValues { (k, v) -> if (profile && k == "id") (if (v is JsonArray) JsonArray(emptyList()) else JsonPrimitive("")) else scrub(v, k) })
            // A name drawn one color per letter is one piece per letter, so no single string holds it. The pieces
            // become one plain piece, which loses the line's colors and keeps the name on the player's machine.
            val whole = if (scrubbed["extra"] is JsonArray) shown(scrubbed) else ""
            if (real.containsMatchIn(whole)) JsonPrimitive(names(whole)) else scrubbed
        }
        is JsonArray -> JsonArray(json.map { scrub(it, key) })
        is JsonPrimitive -> when {
            !json.isString -> json
            key.lowercase() in setOf("uuid", "signature", "value") -> JsonPrimitive("")
            else -> JsonPrimitive(real.replace(json.content) { alias(it.value) })
        }
    }

    /** A capture [line] with its names, skins and typed text removed, or null for a record that is dropped. */
    fun line(line: String): String? {
        val json = Json.parseToJsonElement(line).jsonObject
        if (json.getValue("type").jsonPrimitive.content == "chat") return null
        readCaptureLine(line)?.let { (time, event) -> return captureLine(time, redact(event)) }
        return JsonObject(json + ("data" to scrub(json.getValue("data"), ""))).toString()
    }

    /** [text] with the names this redactor knows replaced by their aliases. */
    fun names(text: String) = real.replace(text) { alias(it.value) }

    fun redact(event: GameEvent): GameEvent = when (event) {
        is SystemMessage -> SystemMessage(typed(event.text))
        is TabList -> TabList(event.rows.map(::names))
        is Sidebar -> Sidebar(names(event.title), event.lines.map(::names))
        is ContainerOpened -> event.copy(title = event.title?.let(::names))
        is SlotChanged -> event.copy(item = event.item?.let(::item))
        WorldChanged -> event
        is HeadPlaced -> event
        is ActionBar -> ActionBar(names(event.text))
        is EntityDied -> event.copy(name = names(event.name))
    }

    private fun item(item: Item) = item.copy(
        name = item.name?.let(::names),
        lore = item.lore.map(::names),
        data = scrub(item.data, "") as JsonObject,
    )
}

/**
 * Writes [capture] to [to] gzipped and redacted: every player name in it, [own] names that the capture may
 * not show, each typed chat message and each skin. The raw `chat` records are dropped, because the `system` events
 * carry the same lines and a raw record's typed text sits in nested components. Returns the number of players renamed.
 */
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
