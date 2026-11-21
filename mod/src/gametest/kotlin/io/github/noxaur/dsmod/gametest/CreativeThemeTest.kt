package io.github.noxaur.dsmod.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.level.GameType

class CreativeThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            ThemedMenus.onServer(world) { it.setGameMode(GameType.CREATIVE) }
            context.waitFor { it.player?.hasInfiniteMaterials() == true }
            context.setScreen { InventoryScreen(Minecraft.getInstance().player!!) }
            context.waitFor { it.gui.screen() is CreativeModeInventoryScreen }
            context.waitTicks(5)
            val title = context.computeOnClient<String, Nothing> { it.gui.screen()!!.title.string }
            ThemedMenus.check(context, title, listOf(Part("the scrollbar's track", 180, 100) { it.well }))
            context.setScreen { null }
        }
    }
}
