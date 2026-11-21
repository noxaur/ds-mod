package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.LoomMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class LoomThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Loom", ::LoomMenu, listOf(Part("the banner's well", 150, 30) { it.well }))
}
