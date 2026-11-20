package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.MerchantMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.trading.ItemCost
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class MerchantThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(
        context, "Villager", ::MerchantMenu, listOf(Part("the trade's button", 35, 21) { it.raised }, Part("the trade list's well", 50, 150) { it.well }),
    ) { player ->
        val offers = MerchantOffers().apply { add(MerchantOffer(ItemCost(Items.EMERALD), ItemStack(Items.BREAD), 5, 1, 0.05f)) }
        player.sendMerchantOffers(player.containerMenu.containerId, offers, 1, 0, true, false)
    }
}
