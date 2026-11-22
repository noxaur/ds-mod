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
        files = "src/{main,debug}/**.{kt,java}",
        except = "src/main/kotlin/**/edge/ScreenHooks.kt",
        forbid = Regex("""\bAFTER_INIT\b"""),
        fix = "Add to a screen with `ScreenHooks.onEveryLayout<ScreenType> { screen, opened -> .. }`. It runs on every layout, " +
            "because Fabric drops a screen's listeners on each one, and `opened` is true only on the first, so state for one " +
            "menu survives a resize. For a container screen, `openedByServer` and `creative` in `ContainerScreens.kt` name which ones.",
        example = "ScreenEvents.AFTER_INIT.register { _, screen, _, _ -> }",
        examples = listOf("ScreenEvents\n    .AFTER_INIT", "FabricScreens.AFTER_INIT.register((client, screen, w, h) -> {});"),
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
        id = "item-data-by-hand",
        files = "src/main/kotlin/**/edge/**.kt",
        except = "src/main/kotlin/**/edge/Items.kt",
        forbid = Regex(""""(?:item_data|item_skin)""""),
        fix = "Read a stack's SkyBlock id with `stack.itemId`, its uuid with `stack.itemUuid` and its skin with `stack.skinId`, " +
            "from edge/Items.kt, which copies the tag once per item. For another field, add it there.",
        example = """val id = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty("item_data")?.getStringOr("item_id", "")""",
        examples = listOf("""tag.getCompoundOrEmpty("extra").getCompoundOrEmpty("item_skin").getStringOr("skinId", "")"""),
    ),
    Rule(
        id = "lore-glyphs-by-hand",
        files = "src/main/kotlin/**/core/**.kt",
        except = "src/main/kotlin/**/core/game/Item.kt",
        forbid = Regex("""[\uE000-\uF8FF]|\\u(?:[Ee][0-9A-Fa-f]{3}|[Ff][0-8][0-9A-Fa-f]{2})"""),
        fix = "Read lore as `item.cleanLore`, which drops the glyphs of the server's resource pack and trims each line once per " +
            "item, and the lines under a header with `item.section(header)`, or `item.sections(regex) { heading, lines -> .. }`.",
        example = """line.filterNot { it in '\uE000'..'\uF8FF' }""",
        examples = listOf("line.filterNot { it in '\uE000'..'\uF8FF' }", "val glyph = '\uE001'"),
    ),
    Rule(
        id = "item-json-by-hand",
        files = "src/main/kotlin/**/core/{game,feature,contribute}/**.kt",
        except = "src/main/kotlin/**/core/game/Item.kt",
        forbid = Regex("""as\?\s*JsonObject|\bdata\s*\[\s*"(?:item_data|extra)""""),
        fix = "Read an item's data with `item.string(\"item_data\", \"rarity\")`, or `item.objectAt(..)` for an object. " +
            "Other JSON reads with `jsonObject` and `jsonPrimitive` inside a `runCatching`.",
        example = """((item.data["item_data"] as? JsonObject)?.get("rarity") as? JsonPrimitive)?.contentOrNull""",
        examples = listOf("(data[\"extra\"] as?\n    JsonObject)", """item.data["item_data"]?.jsonObject?.get("rarity")"""),
        span = Span.FILE,
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
    Rule(
        id = "registry-by-hand",
        files = "src/main/kotlin/**/edge/**.kt",
        except = "src/main/kotlin/**/edge/{Items,Entities}.kt",
        forbid = Regex("""BuiltInRegistries\s*\.\s*(ITEM|ENTITY_TYPE)\b"""),
        fix = "Look an item up with `stackOf(id)`, a barrier when the game has no such item, or `isItem(id)`, and read a " +
            "stack's id with `stack.id`, from edge/Items.kt. Read an entity's type id with `entity.typeId`, and find a type " +
            "with `entityType(id)`, from edge/Entities.kt.",
        example = """Hit(BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString())""",
        examples = listOf("val id = BuiltInRegistries.ITEM.getKey(stack.item)", """Identifier.tryParse(id)?.let(BuiltInRegistries.ITEM::containsKey)""", "BuiltInRegistries\n    .ITEM.getKey(item)"),
        span = Span.FILE,
    ),
    Rule(
        id = "player-slots-by-container",
        files = "src/main/**.{kt,java}",
        except = "src/main/kotlin/**/core/game/ContainerState.kt",
        forbid = Regex("""const\s+val\s+\w+\s*(:\s*Int\s*)?=\s*36(?![\w.])"""),
        fix = "In core, the open container's `menuSlots` and `playerSlots` from `ContainerState` say which slots are the menu's " +
            "and which the player's. In the edge, name a slot with Minecraft's own number, as `InventoryMenu.USE_ROW_SLOT_START`.",
        example = "private const val HOTBAR_FIRST = 36",
        examples = listOf("const val PLAYER_SLOTS=36", "const val PLAYER_SLOTS: Int = 36"),
    ),
    Rule(
        id = "protector-by-stage",
        files = "src/main/kotlin/**/core/feature/**.kt",
        forbid = Regex(""""(Spawned|Resting|Rising)\b"""),
        fix = "Read the Protector's stage with `ProtectorRow.parse` and compare its `ProtectorStage`. Show a stage by its `label`.",
        example = """if (row.stage == "Spawned") return""",
        examples = listOf("""killsLeft("Rising " + tier)"""),
    ),
    Rule(
        id = "slayer-by-line",
        files = "src/main/kotlin/**/core/feature/**.kt",
        forbid = Regex(""""[^"\n]*SLAYER BOSS"""),
        fix = "Read the player's own spawn line with `line(SlayerLine.SPAWNED) { .. }`, and every slayer boss line with " +
            "`line(SlayerLine.head, SlayerLine::parse) { event, now -> .. }`. If the server sends a new one, add it to `SlayerLine` in core/game.",
        example = """feature.line("☠ SLAYER BOSS SPAWNING!") { alert() }""",
    ),
    Rule(
        id = "gametest-point-by-pointer",
        files = "src/gametest/**.{kt,java}",
        except = "src/gametest/**/Pointer.kt",
        forbid = Regex("""\bsetCursorPos\s*\("""),
        fix = "Move the mouse in GUI pixels with `context.point(x, y)`, `context.click(x, y)` or `context.click(widget)`, " +
            "and over a slot with `context.hoverSlot(index)` or `context.hoverOwnSlot(slot)`, from Pointer.kt, which scale by the real GUI scale.",
        example = "context.input.setCursorPos(x * scale, y * scale)",
    ),
    Rule(
        id = "gametest-inventory-by-pointer",
        files = "src/gametest/**.{kt,java}",
        except = "src/gametest/**/Pointer.kt",
        forbid = Regex("""\bInventoryScreen\s*\("""),
        fix = "Open the player's inventory with `context.openInventory()` from Pointer.kt.",
        example = "context.setScreen { InventoryScreen(Minecraft.getInstance().player!!) }",
    ),
    Rule(
        id = "gametest-theme-by-intheme",
        files = "src/gametest/**.{kt,java}",
        except = "src/gametest/**/Themes.kt",
        forbid = Regex("""\bhud\s*\.\s*theme\s*=(?!=)"""),
        fix = "Run a step in a theme with `context.inTheme(Theme.VANILLA) { .. }`, which puts the theme back after it, or take a " +
            "screenshot in both themes with `context.shotInBothThemes(name)`, from Themes.kt.",
        example = "context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }",
        examples = listOf("hud.theme = theme"),
    ),
    Rule(
        id = "gametest-pixels-by-pixels",
        files = "src/gametest/**.{kt,java}",
        except = "src/gametest/**/Pixels.kt",
        forbid = Regex("""\bImageIO\s*\.\s*read\b"""),
        fix = "Read a screenshot with `image(shot)`, a GUI pixel with `context.guiPixel(shot, x, y)`, and compare with " +
            "`countNear`, `brightness`, `distance` and `diff`, from Pixels.kt.",
        example = "val image = ImageIO.read(shot.toFile())",
    ),
    Rule(
        id = "gametest-player-by-scenes",
        files = "src/gametest/**.{kt,java}",
        except = "src/gametest/**/Scenes.kt",
        forbid = Regex("""\bplayerList\s*\.\s*players\b"""),
        fix = "Read the server's player with `server.player` from Scenes.kt, and give it items with `world.give(mapOf(slot to stack))`.",
        example = "server.playerList.players.first().inventory.setItem(9, stack)",
        examples = listOf("server.playerList.players[0]"),
    ),
    Rule(
        id = "gametest-frozen-by-scenes",
        files = "src/gametest/**.{kt,java}",
        except = "src/gametest/**/Scenes.kt",
        forbid = Regex(""""(gamerule advance_(time|weather)|time set )"""),
        fix = "Stop time and the weather with `world.freezeTime()` from Scenes.kt, which sets noon, or `freezeTime(\"midnight\")` " +
            "for another time and `freezeTime(at = null)` to keep the time it is.",
        example = """listOf("gamerule advance_time false", "time set noon").forEach(world.server::runCommand)""",
        examples = listOf("""world.server.runCommand("time set midnight")"""),
    ),
    Rule(
        id = "gametest-egg-by-the-end",
        files = "src/gametest/**.{kt,java}",
        except = "src/gametest/**/InTheEnd.kt",
        forbid = Regex("""Egg has Spawned"""),
        fix = "Show the dragon timer with `context.showDragonTimer(world)`, or send the line alone with `world.eggSpawns()`, from InTheEnd.kt.",
        example = """world.server.runCommand("tellraw @a \"The Egg has Spawned!\"")""",
    ),
    Rule(
        id = "gametest-children-by-screenwidgets",
        files = "src/gametest/**.{kt,java}",
        except = "src/gametest/**/ServerMenus.kt",
        forbid = Regex("""\.\s*children\s*\(\s*\)"""),
        fix = "Find a widget on the client thread with `screen.widgets<W>()`, or from the test with `ServerMenus.widgets<W>(context)`, " +
            "both in ServerMenus.kt. The chat's input is `InChat.input()`, `cursor()` and `clearInput()`.",
        example = "(it.gui.screen() as ChatScreen).children().filterIsInstance<EditBox>().single().value = \"\"",
        examples = listOf("screen.children ()"),
    ),
    Rule(
        id = "send-through-player-commands",
        files = "{src/main/**/edge/**,src/debug/**}",
        except = "src/main/kotlin/**/edge/PlayerCommands.kt",
        forbid = Regex("""(\.|::)\s*send(Command|Chat|UnattendedCommand)\b"""),
        fix = "Send a command for the player with `PlayerCommands.send(command)`, and a chat line with `PlayerCommands.chat(line)`, " +
            "so every command and chat line the mod sends for the player leaves from one place.",
        example = "connection.sendCommand(message.text)",
        examples = listOf("connection.sendChat(message.text)", "Minecraft.getInstance().connection!!::sendCommand", "connection.sendUnattendedCommand(command, null)"),
    ),
    Rule(
        id = "group-by-tree",
        files = "src/main/**.{kt,java}",
        except = "src/main/kotlin/**/core/config/SettingsTree.kt",
        forbid = Regex("""runs\s*(<\s*BetterUi\s*>|\(\s*BetterUi\b)"""),
        fix = "Ask only for the feature itself, as `DsModClient.runs<BackButtons>()`. A feature placed inside a group in " +
            "`SettingsTree` is on only while its group is.",
        example = "private val shown get() = DsModClient.runs<BetterUi>() && DsModClient.runs<MenuTabs>()",
        examples = listOf("DsModClient.INSTANCE.runs(BetterUi.class)", "runs< BetterUi >()", "DsModClient.INSTANCE.runs(\n    BetterUi.class)"),
        span = Span.FILE,
    ),
)
