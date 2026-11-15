package io.github.noxaur.dsmod.gametest

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import io.github.noxaur.dsmod.core.feature.general.InventoryLook
import io.github.noxaur.dsmod.core.feature.general.InventoryRow
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.ItemsRow
import io.github.noxaur.dsmod.core.ui.Theme
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
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

/**
 * The inventory shows on the HUD as a grid, nine to a row, so a head keeps its skin, dyed leather its color and a stack
 * its count, and the chat's free mouse reads an item's tooltip. The hotbar joins it a little apart, and the grid never
 * empties while the inventory is read again after a menu, which takes a few ticks for items with long lore as
 * dragsim.co's have. Read the screenshots: the grid, the tooltip, the hotbar under a gap, and the Inventory look
 * with Themed Menus on in Charcoal and in Vanilla, and off.
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

                /**
                 * The middle of the cell in [row] and [column], in GUI pixels, on the plate or in the inventory's 18px slots
                 * 7px into its panel, with the hotbar row [apart].
                 */
                fun cell(row: Int, column: Int, inventory: Boolean = false, apart: Boolean = false): Pair<Double, Double> =
                    context.computeOnClient<Pair<Double, Double>, Nothing> {
                        val block = checkNotNull(Hud.find(ID)) { "The inventory is not on the HUD" }
                        val inset = if (inventory) 7 + 1 else 0
                        val pitch = if (inventory) 18 else 17
                        val x = (4 + inset + column * pitch + 8) * block.scale
                        val y = (4 + inset + row * pitch + (if (apart) 4 else 0) + 8) * block.scale
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

                // A capture that starts inside a menu has seen none of the inventory, so going back must send it whole.
                ServerMenus.open(context, world, 6, "Big Chest")
                val tapped = ArrayList<GameEvent>()
                context.runOnClient<Nothing> {
                    DsModClient.game.tap = { _, event -> tapped += event }
                }
                try {
                    context.waitTicks(5)
                    context.runOnClient<Nothing> { it.player!!.closeContainer() }
                    context.waitTicks(10)
                } finally {
                    context.runOnClient<Nothing> { DsModClient.game.tap = null }
                }
                val back = tapped.filterIsInstance<ContainerOpened>().last()
                val slots = tapped.drop(tapped.lastIndexOf(back)).count { it is SlotChanged && it.item != null }
                check(back.id == 0 && !back.kept && slots == FILLED) { "A capture started in a menu got the inventory as $back with $slots items" }

                println("InventoryPreviewTest: the HUD drew the plate look in ${drawMicros(context)}µs a frame")

                // The Inventory look is the inventory screen as the player sees it: the theme's panel while Themed Menus
                // is on, and the inventory texture's gray while it is off.
                general.inventoryLook = InventoryLook.INVENTORY
                val inventorySettings = DsModClient.settings.inventory
                val themedBefore = inventorySettings.themedMenus
                val themeBefore = DsModClient.settings.hud.theme
                try {
                    for ((themed, theme) in listOf(true to Theme.CHARCOAL, true to Theme.VANILLA, false to Theme.CHARCOAL)) {
                        inventorySettings.themedMenus = themed
                        DsModClient.settings.hud.theme = theme
                        context.waitTicks(10)
                        check(skin(stackAt(cell(0, 0, inventory = true)))) { "The Inventory look moved the first item off its slot" }
                        check(stackAt(cell(3, 0, inventory = true, apart = true))?.hoverName?.string == "Item 0") { "The Inventory look moved the hotbar row" }
                        val shot = context.takeScreenshot("inventory-preview-inventory-look-${if (themed) theme.name.lowercase() else "vanilla-texture"}")
                        val panel = panelColor(context, shot)
                        val expected = if (themed) theme.panel and 0xFFFFFF else VANILLA_PANEL
                        check(panel == expected) { "The panel is %06X with Themed Menus %s in %s, not %06X".format(panel, themed, theme, expected) }
                        println("InventoryPreviewTest: the HUD drew the Inventory look in ${drawMicros(context)}µs a frame, Themed Menus $themed, $theme")
                    }
                } finally {
                    inventorySettings.themedMenus = themedBefore
                    DsModClient.settings.hud.theme = themeBefore
                }
            } finally {
                general.inventoryPreview = false
                general.inventoryRows = mutableListOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM)
                general.inventoryLook = InventoryLook.PLATE
            }
        }
    }

    /** The HUD's average draw over a second, in µs a frame. */
    private fun drawMicros(context: ClientGameTestContext): Long {
        context.runOnClient<Nothing> { DsModClient.game.metrics.flush() }
        context.waitTicks(20)
        return context.computeOnClient<Long, Nothing> {
            DsModClient.game.metrics.snapshot().timers.getValue("hud.draw").let { timer -> timer.totalNanos / timer.count / 1000 }
        }
    }

    /** The color of the grid's panel in [shot], a few pixels in from its corner, between the edge and the first slot. */
    private fun panelColor(context: ClientGameTestContext, shot: Path): Int {
        val (x, y) = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
            val block = checkNotNull(Hud.find(ID))
            val scale = client.window.guiScale
            ((block.x + (4 + 5) * block.scale) * scale).toInt() to ((block.y + (4 + 5) * block.scale) * scale).toInt()
        }
        return ImageIO.read(shot.toFile()).getRGB(x, y) and 0xFFFFFF
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

        /** The face of the inventory texture's panel. */
        const val VANILLA_PANEL = 0xC6C6C6
        val ITEMS = listOf(Items.DIAMOND_SWORD, Items.OBSIDIAN, Items.BOW, Items.GOLDEN_HOE, Items.NETHER_STAR)

        /** Slots left empty: one in the top row, and one in the hotbar. */
        val EMPTY = setOf(12, 4)

        /** The items in the three rows and the hotbar. */
        const val FILLED = 34

        const val SKIN = "f83159c5bb7d80454d2d646b15774b7116aabcb3af5b6777a27573fc45f7854f"
        val TEXTURES: String = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/$SKIN\"}}}".toByteArray())
    }
}
