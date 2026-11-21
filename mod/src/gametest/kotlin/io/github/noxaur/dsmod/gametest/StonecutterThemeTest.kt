package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.StonecutterMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class StonecutterThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Stonecutter", ::StonecutterMenu, listOf(Part("the scrollbar's track", 125, 60) { it.well }))
}
