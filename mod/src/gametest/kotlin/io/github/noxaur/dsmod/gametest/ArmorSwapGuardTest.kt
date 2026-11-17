package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.ArmorSwapGuard
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items

/**
 * The player holds a chestplate and right clicks. Without the guard it goes on. With the guard it stays in the
 * hand until the player sneaks, and the server never hears of the click that was stopped.
 */
class ArmorSwapGuardTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            fun worn() = world.server.computeOnServer<Boolean, Nothing> { server ->
                server.playerList.players.first().getItemBySlot(EquipmentSlot.CHEST).`is`(Items.DIAMOND_CHESTPLATE)
            }
            fun hold() {
                world.server.runCommand("clear @a")
                world.server.runCommand("item replace entity @a weapon.mainhand with diamond_chestplate")
                context.waitTicks(10)
            }
            // Looking up, so the click lands on no block.
            context.input.lookAt(0f, -80f)
            hold()
            context.input.pressMouse(1)
            context.waitTicks(10)
            check(worn()) { "A right click did not put the chestplate on with the guard off" }

            try {
                DsModClient.feature<ArmorSwapGuard>().enabled = true
                hold()
                context.input.pressMouse(1)
                context.waitTicks(10)
                check(!worn()) { "The guard let the swap through" }
                context.takeScreenshot("armor-swap-guarded")

                context.input.holdKey { it.keyShift }
                context.waitTicks(5)
                context.input.pressMouse(1)
                context.waitTicks(10)
                context.input.releaseKey { it.keyShift }
                check(worn()) { "Sneaking did not let the swap through" }
            } finally {
                DsModClient.feature<ArmorSwapGuard>().enabled = false
            }
        }
    }
}
