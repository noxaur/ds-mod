package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.EnchantmentMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class EnchantingThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Enchant", ::EnchantmentMenu, listOf(Part("the first offer's outline", 110, 14) { it.outline }))
}
