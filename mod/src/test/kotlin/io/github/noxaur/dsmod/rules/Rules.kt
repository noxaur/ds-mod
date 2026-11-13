package io.github.noxaur.dsmod.rules

/**
 * A line of source that must not exist. [files] is a glob under the repo root, [fix] tells the
 * author what to write instead, and [example] is a line the rule must catch.
 */
class Rule(val id: String, val files: String, val forbid: Regex, val fix: String, val example: String)

val rules = listOf(
    Rule(
        id = "core-is-pure",
        files = "src/main/kotlin/**/core/**.kt",
        forbid = Regex("""^import (net\.minecraft|net\.fabricmc|com\.mojang)\."""),
        fix = "core takes plain data. Read Minecraft in the edge package and pass the values in.",
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
        fix = "ds-mod shows information and the player makes every move. The server bans automated actions, " +
            "so ask the user before the mod sends anything for the player.",
        example = "client.connection?.sendCommand(\"warp end\")",
    ),
)
