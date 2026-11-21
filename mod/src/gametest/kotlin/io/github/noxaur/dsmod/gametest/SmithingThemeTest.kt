package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.SmithingMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class SmithingThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Smithing Table", ::SmithingMenu, listOf(Part("the base slot", 27, 49) { it.slot }))
}
