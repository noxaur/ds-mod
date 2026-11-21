package io.github.noxaur.dsmod.rules

/**
 * A line of source that must not exist. [files] is a glob under the repo root, [fix] tells the
 * author what to write instead, and [example] is a line the rule must catch. [except] is a glob for
 * the shared layer that the rule sends everyone else to.
 */
class Rule(
    val id: String,
    val files: String,
    val forbid: Regex,
    val fix: String,
    val example: String,
    val except: String? = null,
    /** Forms beside [example] that the rule must catch, including ones split over lines. */
    val examples: List<String> = emptyList(),
    val span: Span = Span.LINE,
)

/** [LINE] matches one source line. [FILE] matches the whole file, so a hit may span lines. */
enum class Span { LINE, FILE }

val rules = listOf(
    Rule(
        id = "area-by-declaration",
        files = "src/main/kotlin/**/core/feature/**.kt",
        // InfoRows keeps the area rows from a feature, but a TabList or Sidebar event still carries every line.
        forbid = Regex(""""(The End|Village|The Crypts|Catacombs|Dungeon Hub|Void Sepulture)""""),
        fix = "A feature never asks where the player is. Pass its `Area` to `Feature(..)` and `Game` gives it events and a " +
            "readout only there. For a place `Area` lacks, add an entry with the tab list row or the sidebar place a capture shows.",
        example = """if (event.lines.any { "Dungeon Hub" in it.plain }) return""",
    ),
    Rule(
        id = "chat-by-line",
        files = "src/main/kotlin/**/core/feature/**.kt",
        except = "src/main/kotlin/**/core/feature/Feature.kt",
        // Every old shape, `plain.startsWith`, `in plain` and a regex with or without `^`, sat inside this handler.
        // Whitespace and a line break are legal around the type argument and `::class`, and both still subscribe.
        forbid = Regex("""on<\s*SystemMessage\s*>|SystemMessage\s*::\s*class"""),
        fix = "Read a chat line with `line(head) { .. }`, or `line(head, pattern) { (a, b), now -> .. }` to parse it. " +
            "`head` is the line's start, a String or a Regex for a start that varies, because a player can type anything " +
            "after their name. A line whose head matches and whose pattern does not counts as unparsed by itself.",
        example = """on<SystemMessage> { message, _ -> if ("FIREBALL" in message.text.plain) alert() }""",
        examples = listOf(
            "on<\n    SystemMessage\n>",
            "on< SystemMessage >",
            "SystemMessage :: class",
        ),
        span = Span.FILE,
    ),
    Rule(
        id = "text-by-role",
        files = "src/main/**.{kt,java}",
        // PlayerNames strips the `§` codes of an older capture's raw text and reads them to tell an NPC's label. It colors nothing.
        except = "src/main/kotlin/**/{core/text/Text,edge/Chat,core/ui/Theme,core/capture/PlayerNames}.kt",
        // A color in a call is caught in the bytecode by TextLayerTest. A hex literal could be any int there, and a `§` char
        // compiles to one, so both are read in the source.
        // A `_` between hex digits is still one literal, and eight digits are a color with alpha.
        forbid = Regex("""§|\b0x[0-9A-Fa-f](?:_?[0-9A-Fa-f]){5}(?:(?:_?[0-9A-Fa-f]){2})?\b"""),
        fix = "Say what the text means and let the text layer color it: build it with `text { body(..); value(..) }` " +
            "from core.text and print it with `Chat.send`. If no role fits, add one to `Role`.",
        example = "val gold = 0xFF_AA_00",
        examples = listOf("val glass = 0xFF_AA_00_FF", "val gold = 0xFFAA00"),
    ),
    Rule(
        id = "number-by-helper",
        files = "src/main/kotlin/**/core/feature/**.kt",
        forbid = Regex("""\.format\("""),
        fix = "Write a number with `fixed`, `amount`, `grouped` or `short`, and a time with `clock`, `duration` or " +
            "`secondsLeft`, from core.text. If none fits, add one to `core/text/Numbers.kt`.",
        example = """value("%.1f".format(Locale.US, tps))""",
        examples = listOf("""String.format(Locale.US, "%.1fs", seconds)"""),
    ),
    Rule(
        id = "rate-by-helper",
        files = "src/main/kotlin/**/core/feature/**.kt",
        forbid = Regex("""MILLIS_PER_(HOUR|MINUTE)|\b3_?600_?000\b"""),
        fix = "Turn a count over a span into a rate with `perHour(count, millis)`, and a wait into text with " +
            "`duration(millis)` or `secondsLeft(millis)`, from core.text.",
        example = "const val MILLIS_PER_HOUR = 3_600_000.0",
        examples = listOf("val rate = eyes * 3600000 / inEnd", "left / MILLIS_PER_MINUTE"),
    ),
    Rule(
        id = "count-by-helper",
        files = "src/main/kotlin/**/core/**.kt",
        except = "src/main/kotlin/**/core/text/**.kt",
        forbid = Regex(
            """groupValues(\[\d+]|\??\.get\(\d+\))\??\.to(Int|Long|Double|Float)|\.replace\(",", ""\)|""" +
                """(destructured\??\.let\s*\{\s*|\bline\([^{}\n]*\{\s*(?=\([\w\s,]*\)\s*,\s*\w+\s*->)|val\s*)\([\w\s,]*?\b(?<name>\w+)\b[\w\s,]*?\)""" +
                """(\s*,\s*\w+)?\s*(->|=[^\n]*destructured)(?:[^{}]|\{[^{}]*})*?\b\k<name>\??\.to(Int|Long|Double|Float)(OrNull)?\(\)""",
        ),
        fix = "Read a number the server wrote with `count(text)` for a whole one or `number(text)` for one with decimals, " +
            "from core.text. Both take the commas and give null for text that is no number.",
        example = """toNext = left.replace(",", "").toLong()""",
        examples = listOf(
            "position = match.groupValues[1].toInt()",
            "PERCENT.find(it)?.groupValues?.get(1)?.toFloatOrNull()",
            "feature.line(\"Activated \", ACTIVATED) { (level, name), _ -> summoned(name, level.toInt()) }",
            "QUEUE.matchEntire(name)?.destructured?.let { (count, size) ->\n    queued = count.toInt()\n    queueSize = size.toInt()\n}",
            "val (dealt, place) = yours?.destructured ?: return@line\nposition = place.toInt()",
            "val (level, name) = LINE.matchEntire(plain)?.destructured ?: return null\nval color = spans.lastOrNull { it.isNotBlank() }\nreturn Pet(name, level.toIntOrNull())",
        ),
        span = Span.FILE,
    ),
)
