package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.game.GameEvent
import io.github.noxaur.dragen.core.game.ServerSpan
import io.github.noxaur.dragen.core.game.SystemMessage
import io.github.noxaur.dragen.edge.DragenClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class DragonTimerTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val seen = ArrayList<GameEvent>()
            context.runOnClient<Nothing> { DragenClient.game.tap = { _, event -> seen += event } }
            context.showDragonTimer(world)
            context.waitTicks(5)
            check(context.readout(DRAGON_TIMER) == listOf("Eyes: 0/8")) { "Expected Eyes: 0/8, got ${context.readout(DRAGON_TIMER)}" }

            world.server.runCommand(
                """tellraw @a {"text":"§5» ","extra":[{"text":"[VIP] ","extra":[{"text":"Dlnzs","color":"green"}],"color":"green"}," §dplaced a Summoning Eye! §7(§e1§7/§a8§7)"]}""",
            )
            context.waitTicks(5)
            context.runOnClient<Nothing> { DragenClient.game.tap = null }

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
            check(context.readout(DRAGON_TIMER) == listOf("Eyes: 1/8")) { "Expected Eyes: 1/8, got ${context.readout(DRAGON_TIMER)}" }
            context.takeScreenshot("dragon-timer")
        }
    }
}
