package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.CrafterMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class CrafterThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Crafter", ::CrafterMenu, listOf(Part("the unpowered arrow", 100, 43) { it.well }))
}
