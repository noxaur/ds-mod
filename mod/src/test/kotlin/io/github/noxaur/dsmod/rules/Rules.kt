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
)

val rules = listOf(
    Rule(
        id = "no-automation",
        files = "src/main/**.{kt,java}",
        forbid = Regex(
            """\b(sendCommand|sendChat|sendUnattendedCommand|handleContainerInput|handlePickItem\w*|startDestroyBlock|""" +
                """continueDestroyBlock|destroyBlock|useItemOn|useItem|interact|attack|piercingAttack|releaseUsingItem|handleInventoryButtonClick)\(|""" +
                """KeyMapping\.(click|set)\(|\.setDown\(|\bconnection\??\.send\(""",
        ),
        fix = "ds-mod shows information and the player makes every move. Only a feature that DragSim Utils has may " +
            "send for the player. End its line with `rule:no-automation <the feature>`, and ask the user about anything else.",
        example = "client.connection?.sendCommand(\"warp end\")",
    ),
    Rule(
        id = "text-by-role",
        files = "src/main/**.{kt,java}",
        except = "src/main/kotlin/**/{core/text/Text,edge/Chat,core/ui/Theme}.kt",
        // A color in a call is caught in the bytecode by TextLayerTest. A hex literal could be any int there, so it is read in the source.
        forbid = Regex("""\b0x[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?\b"""),
        fix = "Say what the text means and let the text layer color it: build it with `text { body(..); value(..) }` " +
            "from core.text and print it with `Chat.send`. If no role fits, add one to `Role`.",
        example = "val gold = 0xFFAA00",
    ),
)
