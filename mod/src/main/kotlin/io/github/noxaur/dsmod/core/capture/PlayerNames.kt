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

/**
 * Where the server puts a player's name, and where a player's own words start. [Redactor] and `FixtureLeakTest`
 * learn names only here, so they cannot disagree. Each rule is a shape a capture shows, so teach a new one here
 * from a capture, never from a guess.
 */
object PlayerNames {
    /** A name is a whole word, since a Minecraft name is letters, digits and `_`. */
    internal val word = Regex("""\w+""")

    private const val RANK = """(?:\[[^\]]+\] )?"""

    /** The server speaks for its own characters with these tags, as in `[NPC] Mort: Good luck.` */
    private val notPlayer = Regex("""^\[(?:NPC|BOSS)\] """)

    /**
     * Lines that start with a player's name, which is group 1. The first is a chat message: an optional channel such
     * as `Guild > `, the rank, the name, at most one tag or symbol such as `[IRON]` or `✯`, then `: `.
     */
    private val atStart = listOf(
        Regex("""^(?:\w+ > )?$RANK(\w+)(?: \[[^\]]+\]| \S)?: """),
        Regex("""^\[[+-]\] $RANK(\w+)"""),
        Regex("""^$RANK(\w+) (?:has obtained |is now ready|opened a |just activated )"""),
        Regex("""^» $RANK(\w+) placed a Summoning Eye"""),
        Regex("""^\s*☠ $RANK(\w+) was killed by """),
        Regex("""^WOW! $RANK(\w+) found """),
        Regex("""^✦ $RANK(\w+) has woken """),
    )

    /** Names inside a line, such as a boss's damage summary or a booster's activator, each in its own group. */
    private val inside = Regex("""$RANK(\w+) dealt the final blow|Damager $RANK(\w+) - | by $RANK(\w+)$""", RegexOption.MULTILINE)

    private val rankedName = Regex("""^\s*($RANK)(\w+)""")

    /**
     * The server names the textures of its own heads with an `h` and fifteen hex digits. They tell an agent which head
     * a mob wears, and no player is called that.
     */
    private val textureName = Regex("""h[0-9a-f]{15}""")

    // The server writes `You` where the line is about the player themselves, as in `» You placed a Summoning Eye!`.
    private fun isName(name: String) = name.length >= 3 && name != "You" && !name.matches(textureName)

    /**
     * Where the words a player typed sit in [message]. On each line, whatever follows the first [known] name and the
     * next `: ` is typed, whatever stands around the name, as in `[MVP+] Alice [IRON]: x`, `Party > Alice: x` and
     * `You whisper to Bob: x`, because the server never puts a `: ` after a player's name in its own lines. The server's
     * whisper, party and guild lines are in no capture yet, and this needs no list of them. A line of a boss's damage
     * summary names a player and has no `: `, so its figures stay, and what the server's characters say, as in
     * `[NPC] Mort: `, is never typed, even when a player shares the name.
     */
    fun typed(message: String, known: Set<String>): List<IntRange> = buildList {
        var start = 0
        for (line in message.split('\n')) {
            val name = if (notPlayer.containsMatchIn(line)) null else word.findAll(line).firstOrNull { it.value in known }
            val colon = name?.let { line.indexOf(": ", it.range.last + 1) } ?: -1
            if (colon >= 0 && colon + 2 < line.length) add(start + colon + 2 until start + line.length)
            start += line.length + 1
        }
    }

    /**
     * The name a player entity shows, as a match whose group 1 is the rank and group 2 the name, or null when [entity]
     * is no player entity or shows a colored label. The server's NPCs are player entities too. It colors some labels,
     * as in `§e§lCLICK`, and names others plainly, as in `Mort` or `Dungeons`, while a player's name arrives plain with
     * the player's rank, if any. So a ranked name is surely a player's, and a plain one may be an NPC's.
     */
    fun onEntity(entity: JsonObject): MatchResult? {
        if ((entity["entity"] as? JsonPrimitive)?.content != "minecraft:player") return null
        val raw = (entity["name"]?.let { if (it is JsonObject) it["text"] else it } as? JsonPrimitive)?.content ?: return null
        return if (raw.startsWith("§")) null else rankedName.find(raw)?.takeIf { isName(it.groupValues[2]) }
    }

    /** Events store text as `plain`, raw records as `text` with `§` codes. */
    private fun plain(text: JsonElement?): String = when (text) {
        is JsonObject -> plain(text["plain"] ?: text["text"])
        is JsonPrimitive -> text.content.replace(Regex("§."), "")
        else -> ""
    }

    private fun MatchResult.ranges() = groups.drop(1).mapNotNull { it?.range }

    private fun String.names(ranges: List<IntRange>) = ranges.map(::substring).filter(::isName)

    /** Where [text], one chat message, shows a name: whoever it starts with, and whoever it names inside. */
    fun inText(text: String): List<IntRange> {
        if (notPlayer.containsMatchIn(text)) return emptyList()
        val ranges = listOfNotNull(atStart.firstNotNullOfOrNull { it.find(text) }?.groups?.get(1)?.range) +
            inside.findAll(text).flatMap { it.ranges() }
        return ranges.filter { isName(text.substring(it)) }
    }

    /**
     * Where each of one tab list's [rows] shows a name. The player rows are the first column under its title, which is
     * the area, and an info row can name whoever started a booster, as in ` Admin Booster 6m 47s by [ADMIN] name`. The
     * column is 19 rows. A dungeon puts `Class: Mage` rows there too, and the Dungeon Hub has no `Info` title after
     * it. A tab list of one column has the `Info` title inside the first 19 rows, and the players end there.
     */
    fun inTabList(rows: List<String>): List<List<IntRange>> {
        val players = 1 until (rows.indexOfFirst { it.trim() == "Info" }.takeIf { it > 0 } ?: 20).coerceAtMost(20)
        return rows.mapIndexed { i, row ->
            val player = if (i in players && ':' !in row) listOfNotNull(rankedName.find(row)?.groups?.get(2)?.range) else emptyList()
            (player + inside.findAll(row).flatMap { it.ranges() }).filter { isName(row.substring(it)) }
        }
    }

    private val sidebarName = Regex("""^Name: (\w+)""")

    /** Where a [line] of the sidebar shows a name, in its `Name: ` line. */
    fun inSidebar(line: String): IntRange? = sidebarName.find(line)?.groups?.get(1)?.range?.takeIf { isName(line.substring(it)) }

    private val seller = Regex("""^Seller: $RANK(\w+)""")

    /** Where a [line] of an auction item's lore names its seller. */
    fun inLore(line: String): IntRange? = seller.find(line)?.groups?.get(1)?.range?.takeIf { isName(line.substring(it)) }

    /** The name in a [profile], which a skull, a head item or a player entity's head carries. */
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

    /**
     * Every name in [captures] that may be replaced wherever it stands, read one line at a time so a long capture never
     * sits in memory. A word the server writes as its own in any of them counts as its own in all. Two kinds are left out, and [Redactor] replaces them only in a text where a rule finds them. A
     * plain name on a player entity may be an NPC's. And a player may be called what the server calls itself or a
     * place, as a staff account is named after the server: a name the sidebar's title, its `⏣` place, the tab list's
     * area or its header also shows would otherwise turn hundreds of the server's lines into `PlayerN`.
     */
    fun of(vararg captures: Path): Set<String> = learn(captures).let { (names, server) -> names.filterTo(HashSet()) { it !in server } }

    /** Every name a rule finds where a player's name stands in [captures], those the server also writes as its own included. */
    fun found(vararg captures: Path): Set<String> = learn(captures).first

    private fun learn(captures: Array<out Path>): Pair<Set<String>, Set<String>> {
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
                        // An older capture keys the rows of the first column `!aa` to `!at`, the first being the title.
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
