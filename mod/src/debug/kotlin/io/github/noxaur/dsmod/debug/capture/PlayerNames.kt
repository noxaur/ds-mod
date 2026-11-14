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
    private const val TAB_COLUMN = 20
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
                    // The players are the first column under its title, 19 rows. A dungeon puts `Class: Mage` rows there
                    // too, and the Dungeon Hub has no `Info` title after it. An older capture keys the rows `!ab` to `!at`.
                    "tablist" -> data["rows"]?.jsonArray?.map(::plain)?.drop(1)?.take(TAB_COLUMN - 1)?.filter { ':' !in it }?.forEach(::row)
                        ?: data["entries"]?.jsonObject?.values?.filterIsInstance<JsonObject>()
                            ?.filter { plain(it["name"]).matches(Regex("![a][b-z]")) }?.forEach { row(plain(it["text"])) }
                }
            }
        }
    // The server writes `You` where the line is about the player themselves, as in `» You placed a Summoning Eye!`.
    }.filterTo(HashSet()) { it.length >= 3 && it != "You" && !it.matches(Regex("""Player\d+""")) }
}
