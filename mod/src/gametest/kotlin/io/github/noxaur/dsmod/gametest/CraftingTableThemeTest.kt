package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.CraftingMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class CraftingTableThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Crafting", ::CraftingMenu, listOf(Part("the result slot", 121, 32) { it.slot }))
}
