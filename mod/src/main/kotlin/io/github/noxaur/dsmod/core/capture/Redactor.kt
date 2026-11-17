package io.github.noxaur.dsmod.core.capture

import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.FrameRate
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.HeadPlaced
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.game.Hit
import io.github.noxaur.dsmod.core.game.BossesNear
import io.github.noxaur.dsmod.core.game.PetNear
import io.github.noxaur.dsmod.core.game.PingMeasured
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.ServerTime
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
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

/**
 * Rewrites a capture's lines so they hold no player name, no typed message and no skin. Each of [names] becomes
 * `Player1`, `Player2` and so on wherever it stands as a whole word, because a name turns up in places no rule
 * knows, such as a head's item name. Any other name is replaced in a text where a [PlayerNames] rule finds it.
 */
class Redactor(private val names: Set<String>) {
    private val aliases = HashMap<String, String>()
    private val code = Regex("§.") // rule:text-by-role It strips the color codes of an older capture's raw text.

    /** How many names it has replaced so far. */
    val count get() = aliases.size

    private fun alias(name: String) = aliases.getOrPut(name) { "Player${aliases.size + 1}" }

    /** Where [text] holds one of [names], one of [here], the names around it, or a name at a range in [at], which a rule found. */
    private fun hits(text: String, here: Set<String> = emptySet(), at: List<IntRange> = emptyList()) =
        PlayerNames.word.findAll(text).filter { it.value in names || it.value in here || it.range in at }.map { it.range to alias(it.value) }.toList()

    /**
     * [text] with each name replaced. An older capture's raw text carries color codes, which can stand right before a
     * name or between its letters, so a text that names someone loses its codes.
     */
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

    /**
     * Replaces each range of the plain text, which [hits] hold in order without overlap. The replacement goes in the
     * span where its range starts, and what the range covered in the other spans is dropped.
     */
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

    /** [text] with each name replaced and what a player typed in it replaced by `hello`. */
    private fun typed(text: ServerText): ServerText {
        val at = PlayerNames.inText(text.plain)
        val typed = PlayerNames.typed(text.plain, names + at.map(text.plain::substring)).map { it to "hello" }
        val named = hits(text.plain, at = at).filter { (range) -> typed.none { range.first in it.first } }
        return text.replacing((typed + named).sortedBy { it.first.first })
    }

    /** What a text component in Minecraft's JSON shows: its own text, then each piece in `extra`. */
    private fun shown(component: JsonElement?): String = when (component) {
        is JsonObject -> shown(component["text"] as? JsonPrimitive) + shown(component["extra"] as? JsonArray)
        is JsonArray -> component.joinToString("") { shown(it) }
        is JsonPrimitive -> if (component.isString) component.content else ""
        null -> ""
    }

    /** [json] redacted, where [key] is the key it sits under and [here] the names a rule found around it. */
    private fun scrub(json: JsonElement, key: String, here: Set<String>): JsonElement = when (json) {
        is JsonObject -> {
            // A profile's `id` is the player's UUID, as an int array in a skull and a string elsewhere.
            val profile = key.substringAfterLast(':') == "profile"
            val inside = here + listOfNotNull(PlayerNames.onEntity(json)?.groupValues?.get(2), if (profile) PlayerNames.inProfile(json) else null)
            val scrubbed = JsonObject(json.mapValues { (k, v) -> if (profile && k == "id") (if (v is JsonArray) JsonArray(emptyList()) else JsonPrimitive("")) else scrub(v, k, inside) })
            // A name drawn one color per letter is one piece per letter, so no single string holds it. The pieces
            // become one plain piece, which loses the line's colors and keeps the name on the player's machine.
            val whole = if (scrubbed["extra"] is JsonArray) shown(scrubbed) else ""
            val named = names(whole, inside)
            if (named != whole) JsonPrimitive(named) else scrubbed
        }
        // An entity's `uuid`, as in a hover event, can be a player's, and an item's can be looked up on the server's
        // public profiles. Both come as a string or as an int array.
        is JsonArray -> if (key.lowercase() == "uuid") JsonArray(emptyList()) else JsonArray(json.map { scrub(it, key, here) })
        is JsonPrimitive -> when {
            !json.isString -> json
            // `value` and `signature` hold a skin.
            key.lowercase() in setOf("uuid", "signature", "value") -> JsonPrimitive("")
            else -> JsonPrimitive(names(json.content, here))
        }
    }

    /**
     * A capture [line] redacted, or null for a line that is dropped. Typed text is found only in a `system` event, so
     * a raw `chat` record, whose event says the same, is dropped, and so is a `system` line of an older capture that
     * does not read as today's event. Any other event of an older shape is redacted as raw data.
     */
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
        is HeadPlaced, is Hit, is ServerTime, is PingMeasured, is FrameRate -> event
        is ActionBar -> ActionBar(event.text.named())
        is EntityDied -> event.copy(name = event.name.named())
        is PetNear -> PetNear(event.name.named(), item(event.head))
        is BossesNear -> BossesNear(event.bosses.map { it.copy(name = it.name.named()) })
        is FloatingText -> FloatingText(event.text.named())
    }

    private fun item(item: Item) = item.copy(
        name = item.name?.named(),
        lore = item.lore.map { it.named(listOfNotNull(PlayerNames.inLore(it.plain))) },
        data = scrub(item.data, "", emptySet()) as JsonObject,
        skin = null,
    )
}

/**
 * Writes [capture] to [to] gzipped and redacted by a [Redactor] that knows every player name in it and [own], names
 * the capture may not show. It reads the capture twice, a line at a time, so a long one never sits in memory.
 * Returns the number of names replaced.
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
