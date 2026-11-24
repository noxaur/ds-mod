package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.end.dragon.eyeguard.EyeGuard
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.LeverBlock

class EyeGuardTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.inWorld { world ->
            world.commands(
                "clear @a",
                """item replace entity @a weapon.mainhand with stick[custom_name="§5Summoning Eye"]""",
                "execute at @a run setblock ~ ~ ~2 minecraft:lever[face=floor]",
            )
            context.waitTicks(10)
            val lever = world.server.computeOnServer<BlockPos, Nothing> { it.player.blockPosition().offset(0, 0, 2) }
            fun on() = world.server.computeOnServer<Boolean, Nothing> { it.overworld().getBlockState(lever).getValue(LeverBlock.POWERED) }
            fun click() {
                context.input.pressMouse(1)
                context.waitTicks(3)
            }
            context.input.lookAt(lever)
            context.waitTicks(5)
            try {
                DsModClient.feature<EyeGuard>().feature.enabled = false
                click()
                click()
                check(!on()) { "Two clicks did not flip the lever twice with the feature off" }

                DsModClient.feature<EyeGuard>().feature.enabled = true
                click()
                click()
                check(!on()) { "Outside The End the second click was dropped" }

                context.enterTheEnd()
                click()
                click()
                check(on()) { "The second click got through" }
                context.takeScreenshot("eye-guard")

                context.input.holdMouseFor(1, 40)
                context.waitTicks(3)
                check(on()) { "A click got through while the button was held" }

                context.waitTicks(25)
                click()
                check(!on()) { "A click after the guard ran out was dropped" }

                world.server.runCommand("clear @a")
                context.waitTicks(10)
                val stayed = on()
                repeat(10) {
                    context.input.pressMouse(1)
                    context.waitTicks(4)
                }
                check(on() == stayed) { "A repeat got through after the eye left the hand" }

                context.waitTicks(20)
                context.input.pressMouse(1)
                context.waitTicks(3)
                check(on() != stayed) { "A click after the guard lapsed was dropped" }
            } finally {
                DsModClient.feature<EyeGuard>().feature.enabled = true
            }
        }
    }
}
