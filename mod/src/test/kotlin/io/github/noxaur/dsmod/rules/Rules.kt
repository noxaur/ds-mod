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
        id = "server-text-by-harness",
        files = "src/test/kotlin/**.kt",
        except = "src/test/kotlin/**/{harness/Harness,rules/Rules}.kt",
        forbid = Regex("""\bServerText\s*\("""),
        fix = "Build what the server sends with the harness: `text(plain)` or `text(plain, color)` for a line, " +
            "`text(ServerSpan(..), ServerSpan(..))` for a line in several spans, `text()` for an empty one, `lines(..)` for several lines and `chat(line)` for a chat line.",
        example = """val line = SystemMessage(ServerText(ServerSpan("RARE DROP!")))""",
        examples = listOf("ServerText(listOf(ServerSpan(line)))", "ServerText(spans.map { (text, color) -> ServerSpan(text, color) })", "ServerText(emptyList())"),
        span = Span.FILE,
    ),
    Rule(
        id = "readout-by-harness",
        files = "src/test/kotlin/**.kt",
        except = "src/test/kotlin/**/{harness/Harness,rules/Rules}.kt",
        forbid = Regex("""readouts\s*\.\s*(single|first|last|find)"""),
        fix = "Extend `Harness` and read a feature's readout with `readout(feature)`, `hud(feature)` or `rows(feature)`, " +
            "or the one feature's with no argument when the harness runs it `alone`.",
        example = """private fun hud() = game.readouts.single { it.feature.id == "inventory-hud" }.hud""",
        examples = listOf("game.readouts.first { it.feature.id == id }", "game.readouts\n    .single()", "game.readouts.singleOrNull()?.hud"),
        span = Span.FILE,
    ),
    Rule(
        id = "resource-by-harness",
        files = "src/test/kotlin/**.kt",
        except = "src/test/kotlin/**/{harness/Harness,rules/Rules}.kt",
        forbid = Regex("""\bgetResource(AsStream)?\s*\(\s*"/"""),
        fix = "Read a file under `src/test/resources` with the harness's `resource(path)`.",
        example = """checkNotNull(javaClass.getResource("/profiles/profile.json")).readText()""",
        examples = listOf("""javaClass.getResourceAsStream("/profiles/profile.json")"""),
    ),
    Rule(
        id = "no-bang-bang",
        files = "src/{main,debug}/**.kt",
        forbid = Regex("""!!(?![$"=])"""),
        fix = "Say what a null means here: `?: return` when it can't happen in play, a local `val` for a smart cast, " +
            "or `checkNotNull(x) { \"why\" }` when null is a bug.",
        example = "val id = held.after!!.id",
        examples = listOf("run(items[0]!!)", "slots[it]!!.isEmpty"),
    ),
    Rule(
        id = "no-wall-clock-in-core",
        files = "src/main/kotlin/**/core/feature/**.kt",
        forbid = Regex("""System\.currentTimeMillis|System\.nanoTime|Instant\.now\("""),
        fix = "A feature reads time from the `now` its handler receives, so a replay and a game test see the same clock.",
        example = "val at = System.currentTimeMillis()",
        examples = listOf("val at = System.nanoTime()", "val at = Instant.now()"),
    ),
    Rule(
        id = "no-swallowed-catch",
        files = "src/{main,debug}/**.kt",
        forbid = Regex("""catch\s*\([^)]*\)\s*\{\s*\}"""),
        fix = "A catch that does nothing hides the failure. Log it with `log.error(..)`, return what the failure means, " +
            "or use `runCatching { .. }.getOrNull()` where a failure is an expected answer.",
        example = "try { save() } catch (e: IOException) { }",
        examples = listOf("try { save() } catch (e: IOException) {}", "catch (e: Exception) {\n    }"),
        span = Span.FILE,
    ),
    Rule(
        id = "item-id-by-items",
        files = "src/main/kotlin/**/edge/**.kt",
        except = "src/main/kotlin/**/edge/Items.kt",
        forbid = Regex("""BuiltInRegistries\.ITEM\.getKey"""),
        fix = "Read a stack's id with `stack.id` from `edge/Items.kt`, so every id is spelled the same way.",
        example = "val id = BuiltInRegistries.ITEM.getKey(stack.item)",
    ),
    Rule(
        id = "gametest-no-swallowed-wait",
        files = "src/gametest/**.kt",
        forbid = Regex("""runCatching\s*\{[^}]*\bwait(?:For|Until)"""),
        fix = "A wait that times out is a failed test, so let it throw. To check that something does not happen, " +
            "`waitTicks(n)` for as long as it would take and then assert the count.",
        example = "runCatching { context.waitFor({ count(it, word) >= times }, ROUND_TRIP) }",
        examples = listOf("runCatching { context.waitUntil(\"it came\") { done() } }"),
    ),
    Rule(
        id = "gametest-widget-by-servermenus",
        files = "src/gametest/**.kt",
        except = "src/gametest/**/ServerMenus.kt",
        forbid = Regex("""Screens\.getWidgets\("""),
        fix = "Find a widget on the open screen with `ServerMenus.widget<W>(context) { .. }` or `ServerMenus.widgets<W>(context)`, " +
            "which read it on the client thread and skip a hidden one.",
        example = "Screens.getWidgets(screen).filterIsInstance<ImageButton>().single()",
    ),
    Rule(
        id = "gametest-poll-by-waitfor",
        files = "src/gametest/**.kt",
        forbid = Regex("""repeat\(\d+\)\s*\{(?:(?!\n\s*\}\n)[\s\S]){0,250}?waitTicks\(1\)"""),
        fix = "Wait for a condition with `context.waitFor({ .. }, ticks)`, or `context.waitUntil(\"what\") { .. }` from Waits.kt, " +
            "which throws when the time is up. A loop of `waitTicks(1)` ends quietly and the test goes on.",
        example = "repeat(100) { if (done()) return@repeat else context.waitTicks(1) }",
        examples = listOf("repeat(200) {\n    if (done()) return\n    context.waitTicks(1)\n}"),
        span = Span.FILE,
    ),
)
