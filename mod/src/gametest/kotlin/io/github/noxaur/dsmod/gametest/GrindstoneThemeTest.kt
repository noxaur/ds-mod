package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.GrindstoneMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class GrindstoneThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(context, "Grindstone", ::GrindstoneMenu, listOf(Part("the error mark", 102, 37) { it.well })) {
        it.containerMenu.getSlot(0).set(ItemStack(Items.STONE))
    }
}
