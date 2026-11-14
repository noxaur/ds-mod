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
        id = "core-is-pure",
        files = "src/main/kotlin/**/core/**.kt",
        forbid = Regex("""^import (net\.minecraft|net\.fabricmc|com\.mojang|io\.github\.noxaur\.dsmod\.edge)\."""),
        fix = "core takes plain data and returns plain models. Read Minecraft in the edge package and pass the values in, " +
            "and return what the player should see for the edge to draw.",
        example = "import net.minecraft.client.Minecraft",
    ),
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
        except = "src/main/kotlin/**/{core/text/Text,edge/Chat}.kt",
        forbid = Regex(
            """§|\bChatFormatting\.|\bTextColor\.|\.withColor\(|\bComponent\.(literal|translatable)\(|""" +
                """\b0x[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?\b""",
        ),
        fix = "Say what the text means and let the text layer color it: build it with `text { body(..); value(..) }` " +
            "from core.text and print it with `Chat.send`. If no role fits, add one to `Role`.",
        example = "Component.literal(\"§eSold 5 scales!\")",
    ),
)
