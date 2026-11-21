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
        id = "container-by-reads",
        files = "src/{main,debug}/**.{kt,java}",
        except = "src/main/{kotlin/**/edge/ContainerScreens.kt,java/**/edge/mixin/ContainerScreenAccessor.java}",
        forbid = Regex("""\bContainerScreenAccessor\b"""),
        fix = "Read a container screen's place from `ContainerScreens.kt`: `containerLeft`, `containerTop`, `containerWidth`, " +
            "`containerHeight`, `slotUnderMouse`, `screenX(slot)` and `screenY(slot)`, and draw at a slot's own x and y inside " +
            "`screen.drawInContainer(graphics) { .. }`. If a read is missing, add it there.",
        example = "val left = (screen as ContainerScreenAccessor).leftPos",
        examples = listOf("import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor", "ContainerScreenAccessor at = (ContainerScreenAccessor) screen;"),
    ),
    Rule(
        id = "screen-by-hook",
        files = "src/{main/kotlin/**/edge/**.kt,debug/**.kt}",
        except = "src/main/kotlin/**/edge/ScreenHooks.kt",
        forbid = Regex("""ScreenEvents\s*\.\s*AFTER_INIT\b"""),
        fix = "Add to a screen with `ScreenHooks.onEveryLayout<ScreenType> { screen, opened -> .. }`. It runs on every layout, " +
            "because Fabric drops a screen's listeners on each one, and `opened` is true only on the first, so state for one " +
            "menu survives a resize. For a container screen, `openedByServer` and `creative` in `ContainerScreens.kt` name which ones.",
        example = "ScreenEvents.AFTER_INIT.register { _, screen, _, _ -> }",
        examples = listOf("ScreenEvents .AFTER_INIT", "ScreenEvents\n    .AFTER_INIT"),
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
)
