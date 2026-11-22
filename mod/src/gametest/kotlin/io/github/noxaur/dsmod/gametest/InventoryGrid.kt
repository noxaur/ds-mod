package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.feature.general.InventoryHud
import io.github.noxaur.dsmod.core.feature.general.InventoryLayout
import io.github.noxaur.dsmod.core.feature.general.InventoryLook
import io.github.noxaur.dsmod.core.feature.general.InventoryRow
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemLore
import java.nio.file.Path

object InventoryGrid {
    const val ID = "inventory-hud"
    const val DYE = 0x3366CC

    const val VANILLA_PANEL = 0xC6C6C6

    const val FILLED = 34

    private val ITEMS = listOf(Items.DIAMOND_SWORD, Items.OBSIDIAN, Items.BOW, Items.GOLDEN_HOE, Items.NETHER_STAR)

    private val EMPTY = setOf(12, 4)

    fun with(context: ClientGameTestContext, test: (TestSingleplayerContext) -> Unit) {
        val inventoryHud = DsModClient.feature<InventoryHud>()
        context.worldBuilder().create().use { world ->
            world.give((0 until 36).associateWith(::item))
            context.waitFor { DsModClient.game.container.slots.let { slots -> slots.size > 44 && slots.subList(9, 45).count { it != null } == FILLED } }
            inventoryHud.feature.enabled = true
            try {
                context.waitFor { Hud.find(ID) != null }
                test(world)
            } finally {
                inventoryHud.feature.enabled = false
                inventoryHud.shownRows.value = listOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM)
                inventoryHud.look.value = InventoryLook.INVENTORY
                inventoryHud.placement.value = HudDock.FREE
                inventoryHud.key.value = -1
                inventoryHud.keyMode.value = KeyMode.TOGGLE
                inventoryHud.layout.value = InventoryLayout.ROWS
                inventoryHud.opacity.value = 100f
                inventoryHud.pickup.value = false
                context.runOnClient<Nothing> { it.player?.inventory?.selectedSlot = 0 }
            }
        }
    }

    fun cell(context: ClientGameTestContext, row: Int, column: Int, inventory: Boolean = true, gaps: Int = 0, columns: Boolean = false): Pair<Double, Double> =
        context.placed(ID).let { block ->
            val inset = if (inventory) 7 + 1 else 0
            val pitch = if (inventory) 18 else 17
            val along = column * pitch
            val between = row * pitch + gaps * 4
            val x = (4 + inset + (if (columns) between else along) + 8) * block.scale
            val y = (4 + inset + (if (columns) along else between) + 8) * block.scale
            block.x + x.toDouble() to block.y + y.toDouble()
        }

    fun showHotbar(context: ClientGameTestContext) {
        DsModClient.feature<InventoryHud>().shownRows.value = listOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM, InventoryRow.HOTBAR)
        waitForItem(context, "Item 0") { cell(context, 3, 0, gaps = 1) }
    }

    fun stackAt(context: ClientGameTestContext, point: Pair<Double, Double>) =
        context.computeOnClient<ItemStack?, Nothing> { Hud.itemAt(point.first, point.second) }

    fun waitForItem(context: ClientGameTestContext, name: String, point: () -> Pair<Double, Double>) {
        context.waitFor { Hud.find(ID) != null && point().let { p -> Hud.itemAt(p.first, p.second)?.hoverName?.string == name } }
    }

    fun pixel(context: ClientGameTestContext, shot: Path, point: Pair<Double, Double>, dx: Int, dy: Int): Int {
        return context.guiPixel(shot, point.first + dx, point.second + dy)
    }

    fun panelColor(context: ClientGameTestContext, shot: Path): Int {
        val block = context.placed(ID)
        return context.guiPixel(shot, block.x + (4 + 5) * block.scale, block.y + (4 + 5) * block.scale)
    }

    fun item(index: Int): ItemStack {
        if (index in EMPTY) return ItemStack.EMPTY
        val stack = when (index) {
            9 -> ItemStack(Items.PLAYER_HEAD).also { it.set(DataComponents.PROFILE, HeadSkin.profile()) }
            10 -> ItemStack(Items.LEATHER_CHESTPLATE).also { it.set(DataComponents.DYED_COLOR, DyedItemColor(DYE)) }
            15 -> ItemStack(Items.DIAMOND_SWORD).also {
                it.set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { put("item_data", CompoundTag().apply { putString("rarity", "LEGENDARY") }) }))
            }
            22 -> ItemStack(Items.ENDER_EYE, 13)
            else -> ItemStack(ITEMS[index % ITEMS.size])
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Item $index"))
        stack.set(DataComponents.LORE, ItemLore((1..30).map { Component.literal("Line $it of the lore of item $index") }))
        return stack
    }}
