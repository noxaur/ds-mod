package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/** The server sends system messages, and the Dragon Timer shows what the player would read. */
class DragonTimerTest : FabricClientGameTest {
    private fun readout(context: ClientGameTestContext) = context.computeOnClient<List<String>, Nothing> {
        DsModClient.game.readouts.single { it.feature.id == "dragon-timer" }.hud?.rows?.map { row -> row.text.plain } ?: emptyList()
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val seen = ArrayList<GameEvent>()
            context.runOnClient<Nothing> { DsModClient.game.tap = { _, event -> seen += event } }

            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            context.waitTicks(5)
            check(readout(context) == listOf("Eyes: 0/8")) { "Expected Eyes: 0/8, got ${readout(context)}" }

            world.server.runCommand(
                """tellraw @a {"text":"§5» ","extra":[{"text":"[VIP] ","extra":[{"text":"Dlnzs","color":"green"}],"color":"green"}," §dplaced a Summoning Eye! §7(§e1§7/§a8§7)"]}""",
            )
            context.waitTicks(5)
            context.runOnClient<Nothing> { DsModClient.game.tap = null }

            val expected = listOf(
                ServerSpan("» ", 0xAA00AA),
                ServerSpan("[VIP] Dlnzs", 0x55FF55),
                ServerSpan(" "),
                ServerSpan("placed a Summoning Eye! ", 0xFF55FF),
                ServerSpan("(", 0xAAAAAA),
                ServerSpan("1", 0xFFFF55),
                ServerSpan("/", 0xAAAAAA),
                ServerSpan("8", 0x55FF55),
                ServerSpan(")", 0xAAAAAA),
            )
            val eye = seen.filterIsInstance<SystemMessage>().last().text.spans
            check(eye == expected) { "Spans differ.\nExpected: $expected\nGot:      $eye" }
            check(readout(context) == listOf("Eyes: 1/8")) { "Expected Eyes: 1/8, got ${readout(context)}" }
            context.takeScreenshot("dragon-timer")
        }
    }
}
