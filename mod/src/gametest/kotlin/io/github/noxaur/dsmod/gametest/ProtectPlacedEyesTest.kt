package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.LeverBlock

/**
 * A lever stands in for the altar frame, because it shows every click that reaches the server. The player holds
 * an item named as the server names an eye and clicks twice. Without the feature the lever ends where it began,
 * and with it the second click is dropped, as is every click while the button stays down. A click after the
 * guard has run out gets through again.
 */
class ProtectPlacedEyesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.server.runCommand("clear @a")
            world.server.runCommand("""item replace entity @a weapon.mainhand with stick[custom_name="§5Summoning Eye"]""")
            world.server.runCommand("execute at @a run setblock ~ ~ ~2 minecraft:lever[face=floor]")
            context.waitTicks(10)
            val lever = world.server.computeOnServer<BlockPos, Nothing> { it.playerList.players.first().blockPosition().offset(0, 0, 2) }
            fun on() = world.server.computeOnServer<Boolean, Nothing> { it.overworld().getBlockState(lever).getValue(LeverBlock.POWERED) }
            fun click() {
                context.input.pressMouse(1)
                context.waitTicks(3)
            }
            context.input.lookAt(lever)
            context.waitTicks(5)
            try {
                DsModClient.settings.end.protectPlacedEyes = false
                click()
                click()
                check(!on()) { "Two clicks did not flip the lever twice with the feature off" }

                DsModClient.settings.end.protectPlacedEyes = true
                click()
                click()
                check(on()) { "The second click got through" }
                context.takeScreenshot("protect-placed-eyes")

                // Held for two seconds, the button clicks about ten times, and none may get through.
                context.input.holdMouseFor(1, 40)
                context.waitTicks(3)
                check(on()) { "A click got through while the button was held" }

                context.waitTicks(25)
                click()
                check(!on()) { "A click after the guard ran out was dropped" }
            } finally {
                DsModClient.settings.end.protectPlacedEyes = true
            }
        }
    }
}
