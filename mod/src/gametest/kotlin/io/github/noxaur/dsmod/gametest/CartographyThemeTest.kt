package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.CartographyTableMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class CartographyThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Cartography Table", ::CartographyTableMenu, listOf(Part("the map's well", 100, 46) { it.well }))
}
