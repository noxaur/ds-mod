package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.ShulkerBoxMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class ShulkerBoxThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Shulker Box", ::ShulkerBoxMenu, listOf(Part("a slot", 9, 19) { it.slot }))
}
