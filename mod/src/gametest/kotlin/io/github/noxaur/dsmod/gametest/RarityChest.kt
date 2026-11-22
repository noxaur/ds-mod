package io.github.noxaur.dsmod.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData

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
            s.player.openMenu(
                SimpleMenuProvider({ id, inventory, _ -> ChestMenu.sixRows(id, inventory, chest) }, Component.literal("Test Chest")),
            )
        }
        context.waitFor { it.gui.screen() is AbstractContainerScreen<*> && it.gui.screen() !is InventoryScreen }
        context.waitTicks(10)
    }

    private fun item(name: String, rarity: String): ItemStack = ItemStack(Items.DIAMOND_SWORD).also {
        it.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        it.set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { put("item_data", CompoundTag().apply { putString("rarity", rarity) }) }))
    }
}
