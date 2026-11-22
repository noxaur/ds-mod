package io.github.noxaur.dsmod.core.capture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.io.path.useLines

/** @keep Each rule is a shape a capture shows, never a guess, and Redactor and FixtureLeakTest learn names only here, so they
 * cannot disagree. */
object PlayerNames {
    internal val word = Regex("""\w+""")

    private const val RANK = """(?:\[[^\]]+\] )?"""

    private val notPlayer = Regex("""^\[(?:NPC|BOSS)\] """)

    private val speaker = Regex("""^(?:\w+ > )?$RANK(\w+)(?: \[[^\]]+\]| \S)?: """)

    private val outsideTags = Regex("""\[[^\]]*\]|(\w+)""")

    private val atStart = listOf(
        speaker,
        Regex("""^\[[+-]\] $RANK(\w+)"""),
        Regex("""^$RANK(\w+) (?:has obtained |is now ready|opened a |just activated )"""),
        Regex("""^» $RANK(\w+) placed a Summoning Eye"""),
        Regex("""^\s*☠ $RANK(\w+) was killed by """),
        Regex("""^WOW! $RANK(\w+) found """),
        Regex("""^✦ $RANK(\w+) has woken """),
    )

    private val inside = Regex("""$RANK(\w+) dealt the final blow|Damager $RANK(\w+) - | by $RANK(\w+)$""", RegexOption.MULTILINE)

    private val rankedName = Regex("""^\s*($RANK)(\w+)""")

    /** @keep The server names the textures of its own heads with an h and fifteen hex digits, and no player is called that. */
    private val textureName = Regex("""h[0-9a-f]{15}""")

    /** @keep The server writes `You` where the line is about the player themselves, as in `» You placed a Summoning Eye!`. */
    private fun isName(name: String) = name.length >= 3 && name != "You" && !name.matches(textureName)

    class Typed(val words: IntRange, val names: List<IntRange>)

    fun typed(message: String): List<Typed> = buildList {
        var start = 0
        for (line in message.split('\n')) {
            val colon = line.indexOf(": ")
            if (colon > 0 && colon + 2 < line.length && !line[0].isWhitespace() && !notPlayer.containsMatchIn(line)) {
                val names = if (speaker.containsMatchIn(line)) emptyList()
                else outsideTags.findAll(line.substring(0, colon)).mapNotNull { it.groups[1]?.range }.filter { isName(line.substring(it)) }.toList()
                add(Typed(start + colon + 2 until start + line.length, names.map { start + it.first..start + it.last }))
            }
            start += line.length + 1
        }
    }

    /** @keep The server's NPCs are player entities too. It colors some labels, as in `§e§lCLICK`, and names others plainly,
     * as in `Mort`, while a player's name arrives plain with the player's rank, if any. So a ranked name is surely a
     * player's, and a plain one may be an NPC's. */
    fun onEntity(entity: JsonObject): MatchResult? {
        if ((entity["entity"] as? JsonPrimitive)?.content != "minecraft:player") return null
        val raw = (entity["name"]?.let { if (it is JsonObject) it["text"] else it } as? JsonPrimitive)?.content ?: return null
        return if (raw.startsWith("§")) null else rankedName.find(raw)?.takeIf { isName(it.groupValues[2]) }
    }

    private fun plain(text: JsonElement?): String = when (text) {
        is JsonObject -> plain(text["plain"] ?: text["text"])
        is JsonPrimitive -> text.content.replace(Regex("§."), "")
        else -> ""
    }

    private fun MatchResult.ranges() = groups.drop(1).mapNotNull { it?.range }

    private fun String.names(ranges: List<IntRange>) = ranges.map(::substring).filter(::isName)

    fun inText(text: String): List<IntRange> = buildList {
        var start = 0
        for (line in text.split('\n')) {
            if (!notPlayer.containsMatchIn(line)) {
                val ranges = listOfNotNull(atStart.firstNotNullOfOrNull { it.find(line) }?.groups?.get(1)?.range) +
                    inside.findAll(line).flatMap { it.ranges() }
                ranges.filter { isName(line.substring(it)) }.forEach { add(start + it.first..start + it.last) }
            }
            start += line.length + 1
        }
    }

    /** @keep The player rows are the first column under its title, which is the area, and an info row can name whoever
     * started a booster, as in ` Admin Booster 6m 47s by [ADMIN] name`. The column is 19 rows. A dungeon puts `Class:
     * Mage` rows there too. A tab list of one column has the `Info` title inside the first 19 rows, and the players
     * end there. */
    fun inTabList(rows: List<String>): List<List<IntRange>> {
        val players = 1 until (rows.indexOfFirst { it.trim() == "Info" }.takeIf { it > 0 } ?: 20).coerceAtMost(20)
        return rows.mapIndexed { i, row ->
            val player = if (i in players && ':' !in row) listOfNotNull(rankedName.find(row)?.groups?.get(2)?.range) else emptyList()
            (player + inside.findAll(row).flatMap { it.ranges() }).filter { isName(row.substring(it)) }
        }
    }

    private fun Regex.nameIn(line: String) = find(line)?.groups?.get(1)?.range?.takeIf { isName(line.substring(it)) }

    private val sidebarName = Regex("""^Name: (\w+)""")

    fun inSidebar(line: String): IntRange? = sidebarName.nameIn(line)

    private val seller = Regex("""^Seller: $RANK(\w+)""")

    fun inLore(line: String): IntRange? = seller.nameIn(line)

    fun inProfile(profile: JsonObject): String? = (profile["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(::isName)

    private fun MutableSet<String>.inJson(json: JsonElement, key: String = "") {
        when (json) {
            is JsonObject -> {
                if (key.substringAfterLast(':') == "profile") inProfile(json)?.let(::add)
                onEntity(json)?.takeIf { it.groupValues[1].isNotEmpty() }?.let { add(it.groupValues[2]) }
                json.forEach { (k, v) -> inJson(v, k) }
            }
            is JsonArray -> json.forEach { inJson(it, key) }
            is JsonPrimitive -> if (json.isString) plain(json).let { line -> inLore(line)?.let { add(line.substring(it)) } }
        }
    }

    private fun MutableSet<String>.words(text: String) = word.findAll(text).forEach { add(it.value) }

    private val learnedFrom = setOf("system", "chat", "tablist", "sidebar", "tabheader", "entities", "skulls", "itemcomponents", "slot", "container")

    /** @keep A plain name on a player entity may be an NPC's, and a player may be called what the server calls itself or a
     * place, as a staff account is named after the server, so a name the sidebar's title, its `⏣` place, the tab
     * list's area or its header also shows is left out. */
    fun of(vararg captures: Path): Set<String> = learn(*captures).let { (names, server) -> names.filterTo(HashSet()) { it !in server } }

    fun leaked(names: Set<String>, text: String): Set<String> {
        val (plain, other) = names.filterNot { it.matches(alias) }.partition { it.matches(word) }
        val wanted = plain.toSet()
        return word.findAll(text).map { it.value }.filter { it in wanted }.toSortedSet() +
            other.filter { Regex("""(?<!\w)${Regex.escape(it)}(?!\w)""").containsMatchIn(text) }
    }

    val alias = Regex("""Player\d+""")

    internal fun learn(vararg captures: Path): Pair<Set<String>, Set<String>> {
        val names = HashSet<String>()
        val server = HashSet<String>()
        for (capture in captures) capture.useLines { lines ->
            for (line in lines) {
                val type = line.substringAfter("\"type\":\"", "").substringBefore('"')
                if (type !in learnedFrom) continue
                val data = Json.parseToJsonElement(line).jsonObject.getValue("data").jsonObject
                when (type) {
                    "system" -> plain(data["text"]).let { names += it.names(inText(it)) }
                    "chat" -> (data["sender"] as? JsonPrimitive)?.let { names += it.content }
                    "sidebar" -> {
                        val sidebar = data["lines"]?.jsonArray?.map(::plain).orEmpty()
                        sidebar.forEach { line -> inSidebar(line)?.let { names += line.substring(it) } }
                        server.words(plain(data["title"]))
                        sidebar.filter { it.startsWith("⏣") }.forEach { server.words(it) }
                    }
                    "tabheader" -> server.words(plain(data["header"]))
                    "tablist" -> {
                        /** @keep An older capture keys the rows of the first column `!aa` to `!at`, the first being the
                         * title. */
                        val rows = data["rows"]?.jsonArray?.map(::plain)
                            ?: data["entries"]?.jsonObject?.values.orEmpty().filterIsInstance<JsonObject>()
                                .filter { plain(it["name"]).matches(Regex("![a][a-z]")) }.sortedBy { plain(it["name"]) }.map { plain(it["text"]) }
                        inTabList(rows).forEachIndexed { i, ranges -> names += rows[i].names(ranges) }
                        rows.firstOrNull()?.let { server.words(it) }
                        server.words(plain(data["header"]))
                    }
                    else -> names.inJson(data)
                }
            }
        }
        return names.filterTo(HashSet(), ::isName) to server
    }
}
