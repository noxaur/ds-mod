package io.github.noxaur.dsmod.debug.capture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.useLines

/**
 * Where the server puts a player's name, which is the one place [cutFixture] redacts from and
 * `FixtureLeakTest` checks against, so they cannot disagree.
 */
object PlayerNames {
    private val rank = """(?:\[[^\]]+\] )?"""

    /** The start of a line a player typed. Group 1 is the name, and what follows the match is the message. */
    // Guild and party chat put the channel first, as in `Guild > [VIP] name: text`. No capture shows one yet.
    val chat = Regex("""^(?:\w+ > )?$rank(\w+)(?: \S)?: """)

    private val nameAtStart = listOf(
        chat,
        Regex("""^\[[+-]\] $rank(\w+)"""),
        Regex("""^$rank(\w+) has obtained """),
        Regex("""^» $rank(\w+) placed a Summoning Eye"""),
        Regex("""^\s*☠ $rank(\w+) was killed by """),
    )
    private val nameInside = Regex("""$rank(\w+) dealt the final blow|Damager $rank(\w+) - | by $rank(\w+)$""")

    /** Events store text as `plain`, raw records as `text` with `§` codes. */
    private fun plain(text: JsonElement?): String = when (text) {
        is JsonObject -> plain(text["plain"] ?: text["text"])
        is JsonPrimitive -> text.content.replace(Regex("§."), "")
        else -> ""
    }

    /** Every player name in the whole [capture], in the new format and the older raw one. */
    fun of(capture: Path): Set<String> = buildSet {
        fun row(text: String) {
            Regex("""^$rank(\w+)""").find(text.trim())?.let { add(it.groupValues[1]) }
            nameInside.findAll(text).forEach { match -> addAll(match.groupValues.drop(1).filter(String::isNotEmpty)) }
        }
        capture.useLines { lines ->
            for (line in lines) {
                val head = line.take(60)
                if (listOf("system", "tablist", "sidebar").none { "\"type\":\"$it\"" in head }) continue
                val event = Json.parseToJsonElement(line).jsonObject
                val data = event.getValue("data").jsonObject
                when (event.getValue("type").jsonPrimitive.content) {
                    "system" -> plain(data["text"]).let { text ->
                        nameAtStart.firstNotNullOfOrNull { it.find(text) }?.let { add(it.groupValues[1]) }
                        nameInside.findAll(text).forEach { match -> addAll(match.groupValues.drop(1).filter(String::isNotEmpty)) }
                    }
                    "sidebar" -> data["lines"]?.jsonArray?.forEach { sidebarLine ->
                        Regex("""^Name: (\w+)""").find(plain(sidebarLine))?.let { add(it.groupValues[1]) }
                    }
                    // The players are the rows above the info panel. An older capture keys them `!ab` to `!at`.
                    // An info row can name a player too, as in ` Admin Booster 6m 47s by [ADMIN] name`.
                    "tablist" -> data["rows"]?.jsonArray?.map(::plain)?.also { rows ->
                        rows.forEach { text -> nameInside.findAll(text).forEach { match -> addAll(match.groupValues.drop(1).filter(String::isNotEmpty)) } }
                    }?.drop(1)?.takeWhile { it.trim() != "Info" }?.forEach(::row)
                        ?: data["entries"]?.jsonObject?.values?.filterIsInstance<JsonObject>()
                            ?.filter { plain(it["name"]).matches(Regex("![a][b-z]")) }?.forEach { row(plain(it["text"])) }
                }
            }
        }
    // The server writes `You` where the line is about the player themselves, as in `» You placed a Summoning Eye!`.
    }.filterTo(HashSet()) { it.length >= 3 && it != "You" && !it.matches(Regex("""Player\d+""")) }
}
