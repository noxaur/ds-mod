package io.github.noxaur.dsmod.gametest

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import io.github.noxaur.dsmod.core.feature.general.InventoryRow
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.HudLook
import io.github.noxaur.dsmod.core.hud.ItemsRow
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import java.util.Base64
import java.util.UUID

/**
 * The inventory shows on the HUD as a grid, nine to a row, so a head keeps its skin, dyed leather its color and a stack
 * its count, and the chat's free mouse reads an item's tooltip. The hotbar joins it a little apart, and the grid never
 * empties while the inventory is read again after a menu, which takes a few ticks for items with long lore as
 * dragsim.co's have. Read the screenshots: the grid, the tooltip, the hotbar under a gap, and the hotbar look.
 */
class InventoryPreviewTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val general = DsModClient.settings.general
        context.worldBuilder().create().use { world ->
            world.server.runOnServer<Nothing> { server ->
                val inventory = server.playerList.players.first().inventory
                // The player's inventory holds the hotbar in 0 to 8 and the three rows from 9.
                for (index in 0 until 36) inventory.setItem(index, item(index))
            }
            context.waitTicks(10)
            general.inventoryPreview = true
            try {
                context.waitTicks(10)

                /** The middle of the cell in [row] and [column], in GUI pixels, in plate or hotbar slots, with the hotbar row [apart]. */
                fun cell(row: Int, column: Int, hotbar: Boolean = false, apart: Boolean = false): Pair<Double, Double> =
                    context.computeOnClient<Pair<Double, Double>, Nothing> {
                        val block = checkNotNull(Hud.find(ID)) { "The inventory is not on the HUD" }
                        val inset = if (hotbar) 3 else 0
                        val x = (4 + inset + column * (if (hotbar) 20 else 17) + 8) * block.scale
                        val y = (4 + inset + row * (if (hotbar) 22 else 17) + (if (apart) 4 else 0) + 8) * block.scale
                        block.x + x.toDouble() to block.y + y.toDouble()
                    }
                fun stackAt(point: Pair<Double, Double>) = context.computeOnClient<ItemStack?, Nothing> { Hud.itemAt(point.first, point.second) }

                check(skin(stackAt(cell(0, 0)))) { "The first item's icon lost its skin" }
                check(stackAt(cell(0, 1))?.get(DataComponents.DYED_COLOR)?.rgb() == DYE) { "The leather lost its dye" }
                check(stackAt(cell(1, 4))?.count == 13) { "The middle row's fifth stack is not 13 strong" }
                check(stackAt(cell(2, 8))?.hoverName?.string == "Item 35") { "The last cell is not the bottom row's last slot" }
                check(stackAt(cell(0, 3)) == null) { "An empty slot has an icon" }
                context.takeScreenshot("inventory-preview")

                context.runOnClient<Nothing> { it.gui.setScreen(ChatScreen("", false)) }
                context.waitForScreen(ChatScreen::class.java)
                val dyed = cell(0, 1)
                val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
                context.input.setCursorPos(dyed.first * scale, dyed.second * scale)
                context.waitTicks(5)
                context.takeScreenshot("inventory-preview-tooltip")
                context.runOnClient<Nothing> { it.gui.setScreen(null) }

                general.inventoryRows = InventoryRow.entries.toMutableList()
                context.waitTicks(10)
                check(stackAt(cell(3, 0, apart = true))?.hoverName?.string == "Item 0") { "The hotbar's first item is not under the gap" }
                context.takeScreenshot("inventory-preview-hotbar-row")

                // A slot that changes hands the whole grid to the HUD again, which rebuilds each stack once.
                val (fresh, again) = context.computeOnClient<Pair<Long, Long>, Nothing> {
                    val block = checkNotNull(DsModClient.game.readouts.first { readout -> readout.feature.id == ID }.hud)
                    val group = HudGroup("probe", 0, 0)
                    fun micros(rounds: Int, show: () -> Unit): Long {
                        val start = System.nanoTime()
                        repeat(rounds) { show() }
                        return (System.nanoTime() - start) / rounds / 1000
                    }
                    repeat(5) { Hud.show("probe", group, block) }
                    val fresh = micros(20) { Hud.hide("probe"); Hud.show("probe", group, block) }
                    val again = micros(20) { Hud.show("probe", group, block) }
                    Hud.hide("probe")
                    fresh to again
                }
                println("InventoryPreviewTest: handing the HUD a grid of $FILLED items took ${fresh}µs new and ${again}µs again")

                // Back in the inventory after a menu, the long-lored items are read again over a few ticks, and the grid keeps them all.
                var sampling = false
                val shown = ArrayList<Int>()
                ClientTickEvents.END_CLIENT_TICK.register {
                    if (!sampling) return@register
                    val block = DsModClient.game.readouts.first { readout -> readout.feature.id == ID }.hud
                    shown += block?.rows?.sumOf { row -> (row as ItemsRow).items.count { item -> item != null } } ?: 0
                }
                ServerMenus.open(context, world, 6, "Big Chest")
                context.runOnClient<Nothing> {
                    sampling = true
                    it.player!!.closeContainer()
                }
                context.waitTicks(20)
                context.runOnClient<Nothing> { sampling = false }
                check(shown.isNotEmpty() && shown.all { it == FILLED }) { "The grid did not hold all $FILLED items as the inventory was read again: $shown" }

                general.inventoryLook = HudLook.HOTBAR
                context.waitTicks(10)
                check(skin(stackAt(cell(0, 0, hotbar = true)))) { "The hotbar look moved the first item off its slot" }
                check(stackAt(cell(3, 0, hotbar = true, apart = true))?.hoverName?.string == "Item 0") { "The hotbar look moved the hotbar row" }
                context.takeScreenshot("inventory-preview-hotbar-look")
            } finally {
                general.inventoryPreview = false
                general.inventoryRows = mutableListOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM)
                general.inventoryLook = HudLook.PLATE
            }
        }
    }

    /** The item for inventory [index], named after it and with lore as long as dragsim.co's, or none in a few slots. */
    private fun item(index: Int): ItemStack {
        if (index in EMPTY) return ItemStack.EMPTY
        val stack = when (index) {
            9 -> ItemStack(Items.PLAYER_HEAD).also { it.set(DataComponents.PROFILE, profile()) }
            10 -> ItemStack(Items.LEATHER_CHESTPLATE).also { it.set(DataComponents.DYED_COLOR, DyedItemColor(DYE)) }
            22 -> ItemStack(Items.ENDER_EYE, 13)
            else -> ItemStack(ITEMS[index % ITEMS.size])
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Item $index"))
        stack.set(DataComponents.LORE, ItemLore((1..30).map { Component.literal("Line $it of the lore of item $index") }))
        return stack
    }

    private fun profile() = ResolvableProfile.createResolved(
        GameProfile(UUID.nameUUIDFromBytes(SKIN.toByteArray()), "", PropertyMap(ImmutableMultimap.of("textures", Property("textures", TEXTURES)))),
    )

    /** Whether [stack] wears [SKIN], which the HUD rebuilt from the mod's own record of the item. */
    private fun skin(stack: ItemStack?): Boolean {
        val value = stack?.get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value() ?: return false
        return SKIN in String(Base64.getDecoder().decode(value))
    }

    private companion object {
        const val ID = "inventory-preview"
        const val DYE = 0x3366CC
        val ITEMS = listOf(Items.DIAMOND_SWORD, Items.OBSIDIAN, Items.BOW, Items.GOLDEN_HOE, Items.NETHER_STAR)

        /** Slots left empty: one in the top row, and one in the hotbar. */
        val EMPTY = setOf(12, 4)

        /** The items in the three rows and the hotbar. */
        const val FILLED = 34

        const val SKIN = "f83159c5bb7d80454d2d646b15774b7116aabcb3af5b6777a27573fc45f7854f"
        val TEXTURES: String = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/$SKIN\"}}}".toByteArray())
    }
}
