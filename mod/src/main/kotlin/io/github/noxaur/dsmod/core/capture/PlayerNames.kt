package io.github.noxaur.dsmod.core.capture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

    /** What may stand between a name and the `: ` that starts its message: a tag such as `[IRON]`, one symbol, or the whisper verb. */
    private val beforeColon = Regex("""(?: whispers to you| \[[^\]]+\]| \S)?: """)

    /**
     * Where the text a player typed starts in [line], or null when it holds none. A line that [chat] matches has
     * one. So does any line that shows a [known] name directly before a `: `, whatever stands before the name, as in
     * `From [MVP+] Alice: x` and `You whisper to Bob: x`. A name followed by anything longer, such as the `Your
     * Damage: ` of a boss summary, is not speaking. The server's own whisper, party and message lines are in no
     * capture yet, so `whispers to you` is the one verb listed.
     */
    fun typedFrom(line: String, known: Regex): Int? {
        chat.find(line)?.let { return it.range.last + 1 }
        return known.findAll(line).firstNotNullOfOrNull { name -> beforeColon.matchAt(line, name.range.last + 1)?.range?.last?.plus(1) }
    }

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

    /** The names that [text], one line of chat, shows: whoever it starts with, and whoever it names inside. */
    fun inText(text: String): Set<String> = buildSet {
        nameAtStart.firstNotNullOfOrNull { it.find(text) }?.let { add(it.groupValues[1]) }
        nameInside.findAll(text).forEach { match -> addAll(match.groupValues.drop(1).filter(String::isNotEmpty)) }
    }

    /**
     * The names a tab list's [rows] show: the player rows of the first column under its title, which is the area, and
     * whoever an info row names, as in ` Admin Booster 6m 47s by [ADMIN] name`. The column is 19 rows. A dungeon puts
     * `Class: Mage` rows there too, and the Dungeon Hub has no `Info` title after it. A tab list of one column has the
     * `Info` title inside the first 19 rows, and the players end there.
     */
    fun inTabList(rows: List<String>): Set<String> = buildSet {
        rows.forEach { text -> nameInside.findAll(text).forEach { match -> addAll(match.groupValues.drop(1).filter(String::isNotEmpty)) } }
        rows.drop(1).take(TAB_COLUMN - 1).takeWhile { it.trim() != "Info" }.filter { ':' !in it }.forEach { text -> rankedName.find(text.trim())?.let { add(it.groupValues[1]) } }
    }.filterTo(HashSet(), ::isName)

    private val rankedName = Regex("""^$rank(\w+)""")

    // The server writes `You` where the line is about the player themselves, as in `» You placed a Summoning Eye!`.
    private fun isName(name: String) = name.length >= 3 && name != "You" && !name.matches(Regex("""Player\d+"""))

    /** The names in every `profile` object under [json], which a skull or a head item carries. */
    private fun profileNames(json: JsonElement, key: String = ""): List<String> = when (json) {
        is JsonObject -> json.flatMap { (k, v) -> profileNames(v, k) } +
            if (key.substringAfterLast(':') == "profile") listOfNotNull((json["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content) else emptyList()
        is JsonArray -> json.flatMap { profileNames(it, key) }
        is JsonPrimitive -> emptyList()
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
                if (listOf("system", "tablist", "sidebar", "entities", "skulls", "itemcomponents").none { "\"type\":\"$it\"" in head }) continue
                val event = Json.parseToJsonElement(line).jsonObject
                val data = event.getValue("data").jsonObject
                when (event.getValue("type").jsonPrimitive.content) {
                    "system" -> addAll(inText(plain(data["text"])))
                    // A player standing in view is named by the entity, which no chat line need mention. The server's
                    // NPCs are player entities too, and it colors their labels, as in `§e§lCLICK`. A real player's
                    // name arrives plain, so a colored one is a label, and redacting it would blank a common word.
                    "entities" -> data["entries"]?.jsonObject?.values?.filterIsInstance<JsonObject>()
                        ?.filter { it["entity"]?.jsonPrimitive?.content == "minecraft:player" }
                        ?.filterNot { ((it["name"] as? JsonObject)?.get("text") as? JsonPrimitive)?.content?.startsWith("§") == true }
                        ?.forEach { row(plain(it["name"])) }
                        .also { addAll(profileNames(data)) }
                    "skulls", "itemcomponents" -> addAll(profileNames(data))
                    "sidebar" -> data["lines"]?.jsonArray?.forEach { sidebarLine ->
                        Regex("""^Name: (\w+)""").find(plain(sidebarLine))?.let { add(it.groupValues[1]) }
                    }
                    // An older capture keys the player rows `!ab` to `!at`.
                    "tablist" -> data["rows"]?.jsonArray?.map(::plain)?.let { addAll(inTabList(it)) }
                        ?: data["entries"]?.jsonObject?.values?.filterIsInstance<JsonObject>()
                            ?.filter { plain(it["name"]).matches(Regex("![a][b-z]")) }?.forEach { row(plain(it["text"])) }
                }
            }
        }
    }.filterTo(HashSet(), ::isName)
}
