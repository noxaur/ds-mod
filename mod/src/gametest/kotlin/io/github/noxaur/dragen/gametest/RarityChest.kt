package io.github.noxaur.dragen.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object RarityChest {
    fun open(context: ClientGameTestContext, server: TestServerContext) {
        server.runOnServer<Nothing> { s ->
            val chest: Container = SimpleContainer(54).also {
                it.setItem(0, item("Common Sword", "COMMON"))
                it.setItem(2, item("Uncommon Sword", "UNCOMMON"))
                it.setItem(4, item("Rare Sword", "RARE"))
                it.setItem(6, item("Epic Sword", "EPIC"))
                it.setItem(8, item("Legendary Sword", "LEGENDARY"))
                it.setItem(19, item("Mythic Sword", "MYTHIC"))
                it.setItem(21, item("Divine Sword", "DIVINE"))
                it.setItem(23, item("Special Sword", "SPECIAL"))
                it.setItem(25, ItemStack(Items.STONE))
            }
            s.player.openMenu(ServerMenus.sixRows(chest, "Test Chest"))
        }
        context.waitFor { it.gui.screen() is AbstractContainerScreen<*> && it.gui.screen() !is InventoryScreen }
        context.waitTicks(10)
    }

    private fun item(name: String, rarity: String): ItemStack = ServerMenus.rarity(ServerMenus.named(Items.DIAMOND_SWORD, name), rarity)
}
