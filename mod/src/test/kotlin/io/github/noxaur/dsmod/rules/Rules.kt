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
        id = "kills-by-own-kills",
        files = "src/main/kotlin/**/core/feature/**.kt",
        except = "src/main/kotlin/**/core/feature/general/OwnKills.kt",
        forbid = Regex("""\bCombatXp\b"""),
        fix = "Count the player's own kills with `OwnKills(feature, { death -> .. }) { count, xp, now -> .. }`, which " +
            "matches each death to the Combat XP action bar behind it and reads the XP.",
        example = "if (CombatXp.MARK !in bar.text.plain) return@on",
        examples = listOf("bar.text.plain.let(CombatXp::gained)"),
    ),
    Rule(
        id = "container-by-reader",
        files = "src/main/kotlin/**/core/feature/**.kt",
        except = "src/main/kotlin/**/core/feature/general/ContainerReader.kt",
        forbid = Regex("""on<\s*(ContainerOpened|SlotChanged|WorldChanged)\s*>\s*\{\s*\w+\s*,\s*\w+\s*->\s*\w+\s*=\s*true\s*\}"""),
        fix = "Mark slots with `SlotMarks(feature) { slot, item, now -> .. }`, which rebuilds the marks only when the " +
            "container changed or `invalidate()` says so. To read a container's items again after it changed, ask " +
            "`ContainerReader(feature).changed()`.",
        example = "feature.on<SlotChanged> { _, _ -> changed = true }",
        examples = listOf("feature.on<ContainerOpened> { event, _ -> stale = true }", "feature.on< WorldChanged > {\n    _, _ -> stale = true\n}"),
        span = Span.FILE,
    ),
    Rule(
        id = "pet-by-current-pet",
        files = "src/main/kotlin/**/core/feature/**.kt",
        except = "src/main/kotlin/**/core/feature/general/CurrentPet.kt",
        forbid = Regex("""\b(Pet\s*(\.|::)\s*(STANDS|worn)|PetCard\s*(\.|::)\s*of)\b|"Pet:""""),
        fix = "Follow the pet the player has out with `CurrentPet(feature, { before -> .. })`, which reads the sidebar, " +
            "the Pets menu's card and the heads of pets nearby, and forgets the pet when the player leaves.",
        example = "val card = event.item?.let(PetCard::of)",
        examples = listOf("feature.readEntities(Pet.STANDS)", "val (name, head) = Pet.worn(seen)", """startsWith("Pet:")"""),
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
