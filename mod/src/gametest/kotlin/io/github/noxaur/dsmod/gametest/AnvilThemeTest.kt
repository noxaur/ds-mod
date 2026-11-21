package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class AnvilThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(
        context, "Anvil", ::AnvilMenu, listOf(Part("the error mark", 109, 51) { it.well }, Part("the name field", 61, 22) { it.well }),
    ) { it.containerMenu.getSlot(0).set(ItemStack(Items.STONE)) }
}
