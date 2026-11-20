package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.DispenserMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class DispenserThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Dispenser", ::DispenserMenu, listOf(Part("a slot", 63, 18) { it.slot }))
}
