package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.BeaconMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class BeaconThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(
        context, "Beacon", { id, inventory -> BeaconMenu(id, inventory, ThemedMenus.data(0, -1, -1), ContainerLevelAccess.NULL) },
        listOf(Part("the primary powers' well", 12, 95) { it.well }, Part("a power's outline", 64, 22) { it.outline }),
    )
}
