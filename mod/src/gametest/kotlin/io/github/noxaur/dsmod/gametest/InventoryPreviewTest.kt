package io.github.noxaur.dsmod.gametest

import com.google.common.collect.ImmutableMultimap
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.feature.general.InventoryLook
import io.github.noxaur.dsmod.core.feature.general.InventoryRow
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.ItemsRow
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.InventoryKey
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import org.lwjgl.glfw.GLFW
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

/**
 * The inventory shows on the HUD as a grid, nine to a row, so a head keeps its skin, dyed leather its color and a stack
 * its count, and the chat's free mouse reads an item's tooltip. The hotbar joins it a little apart, and the grid never
 * empties while the inventory is read again after a menu, which takes a few ticks for items with long lore as
 * dragsim.co's have. Read the screenshots: the grid, the tooltip, the hotbar under a gap, and the Inventory look
 * with Themed Menus on in Charcoal, in Vanilla and as glass, and off.
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
                fun cell(row: Int, column: Int, inventory: Boolean = true, apart: Boolean = false): Pair<Double, Double> =
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
                // is on, its glass under the Glass menu look, and the inventory texture's gray while it is off.
                general.inventoryLook = InventoryLook.INVENTORY
                val inventorySettings = DsModClient.settings.inventory
                val themedBefore = inventorySettings.themedMenus
                val themeBefore = DsModClient.settings.hud.theme
                val menuLookBefore = inventorySettings.menuLook
                try {
                    val states = listOf(
                        Triple(true, Theme.CHARCOAL, MenuLook.SOLID),
                        Triple(true, Theme.VANILLA, MenuLook.SOLID),
                        Triple(true, Theme.CHARCOAL, MenuLook.GLASS),
                        Triple(false, Theme.CHARCOAL, MenuLook.SOLID),
                    )
                    for ((themed, theme, menuLook) in states) {
                        inventorySettings.themedMenus = themed
                        inventorySettings.menuLook = menuLook
                        DsModClient.settings.hud.theme = theme
                        context.waitTicks(10)
                        check(skin(stackAt(cell(0, 0, inventory = true)))) { "The Inventory look moved the first item off its slot" }
                        check(stackAt(cell(3, 0, inventory = true, apart = true))?.hoverName?.string == "Item 0") { "The Inventory look moved the hotbar row" }
                        val name = when {
                            !themed -> "vanilla-texture"
                            menuLook == MenuLook.GLASS -> "glass"
                            else -> theme.name.lowercase()
                        }
                        val shot = context.takeScreenshot("inventory-preview-inventory-look-$name")
                        // Glass shows the world through it, so only a solid panel has one color to check.
                        if (menuLook == MenuLook.SOLID) {
                            val panel = panelColor(context, shot)
                            val expected = if (themed) theme.panel and 0xFFFFFF else VANILLA_PANEL
                            check(panel == expected) { "The panel is %06X with Themed Menus %s in %s, not %06X".format(panel, themed, theme, expected) }
                        }
                        println("InventoryPreviewTest: the HUD drew the Inventory look in ${drawMicros(context)}µs a frame, Themed Menus $themed, $theme")
                    }
                } finally {
                    inventorySettings.themedMenus = themedBefore
                    inventorySettings.menuLook = menuLookBefore
                    DsModClient.settings.hud.theme = themeBefore
                }
                general.inventoryLook = InventoryLook.INVENTORY
                context.waitTicks(10)

                // The hotbar row marks the slot the player holds, as the hotbar does, and moves with it.
                context.runOnClient<Nothing> { it.player!!.inventory.selectedSlot = 2 }
                context.waitTicks(5)
                val heldShot = context.takeScreenshot("inventory-preview-held")
                val ring = Theme.CHARCOAL.accent and 0xFFFFFF
                check(pixel(context, heldShot, cell(3, 2, apart = true), 0, -9) == ring) { "The held slot has no ring" }
                check(pixel(context, heldShot, cell(3, 3, apart = true), 0, -9) != ring) { "A slot the player does not hold has a ring" }

                // Rarity Marks paints a Legendary item's slot as it does in the inventory, and leaves a plain one alone.
                check(pixel(context, heldShot, cell(0, 6), -7, -7) != pixel(context, heldShot, cell(1, 2), -7, -7)) {
                    "The Legendary sword's slot looks like the plain sword's"
                }

                // A menu shows the inventory itself, so the grid steps aside, under glass where the HUD would show through too.
                inventorySettings.menuLook = MenuLook.GLASS
                inventorySettings.glassOpacity = 0f
                try {
                    // Glass menus neither dim nor blur, so the head in the grid's first slot shows plainly until the grid steps aside.
                    val head = cell(0, 0)
                    val open = context.takeScreenshot("inventory-preview-before-menu")
                    ServerMenus.open(context, world, 1, "Small Chest")
                    val behind = context.takeScreenshot("inventory-preview-menu-open")
                    check(pixel(context, behind, head, 0, 0) != pixel(context, open, head, 0, 0)) { "The grid still shows under an open menu" }
                    context.runOnClient<Nothing> { it.player!!.closeContainer() }
                    context.waitTicks(5)

                    // On the HUD's plate, the plate steps aside with the icons.
                    general.inventoryLook = InventoryLook.PLATE
                    context.waitTicks(10)
                    val margin = context.computeOnClient<Pair<Double, Double>, Nothing> {
                        val block = checkNotNull(Hud.find(ID))
                        block.x + 2.0 * block.scale to block.y + block.height / 2.0 * block.scale
                    }
                    val plated = context.takeScreenshot("inventory-preview-plate-before-menu")
                    ServerMenus.open(context, world, 1, "Small Chest")
                    val unplated = context.takeScreenshot("inventory-preview-plate-menu-open")
                    // The plate darkens the world behind it, so the same pixel without it is lighter.
                    check(pixel(context, unplated, margin, 0, 0) != pixel(context, plated, margin, 0, 0)) { "The grid's plate stays under an open menu" }
                    context.runOnClient<Nothing> { it.player!!.closeContainer() }
                    general.inventoryLook = InventoryLook.INVENTORY
                    context.waitTicks(5)
                } finally {
                    inventorySettings.menuLook = menuLookBefore
                    inventorySettings.glassOpacity = 60f
                }

                // Docked, the grid sits right of the hotbar past the hotbar's own gap, scaled down to the room there.
                general.inventoryPlacement = HudDock.RIGHT_OF_HOTBAR
                context.waitTicks(10)
                val (width, height) = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.guiScaledWidth to it.window.guiScaledHeight }
                val (first, last) = context.computeOnClient<Pair<Pair<Int, Int>?, Pair<Int, Int>?>, Nothing> {
                    var first: Pair<Int, Int>? = null
                    var last: Pair<Int, Int>? = null
                    for (y in 0 until height) for (x in 0 until width) {
                        val name = Hud.itemAt(x + 0.5, y + 0.5)?.hoverName?.string
                        if (name == "Item 0" && first == null) first = x to y
                        if (name == "Item 8") last = x to y
                    }
                    first to last
                }
                check(first != null && last != null) { "The docked grid's hotbar row is not on the screen" }
                check(first!!.first >= width / 2 + 91 + 7 && last!!.first < width) { "The docked grid is not right of the hotbar: $first to $last" }
                check(last.second >= height - 22) { "The docked grid's hotbar row is not level with the hotbar: $last" }
                context.takeScreenshot("inventory-preview-docked-right")
                general.inventoryPlacement = HudDock.FREE
                context.waitTicks(10)

                // A key the player binds shows and hides the grid, by a press under Toggle and while it is held under Hold.
                val key = GLFW.GLFW_KEY_K
                general.inventoryKey = key
                fun shown() = context.computeOnClient<Boolean, Nothing> { Hud.find(ID) != null }
                context.waitTicks(2)
                check(shown()) { "Binding a key hid the grid before its first press" }
                context.input.pressKey(key)
                context.waitTicks(2)
                check(!shown()) { "Toggle's press did not hide the grid" }
                // A key held down repeats its event, as the game sends it, and a repeat is not another press.
                context.input.holdKey(key)
                context.waitTicks(2)
                check(shown()) { "Toggle's press after a release did not show the grid" }
                context.runOnClient<Nothing> { InventoryKey.pressed(InputConstants.Type.KEYSYM.getOrCreate(key)) }
                context.waitTicks(2)
                check(shown()) { "A repeat of the held key toggled the grid again" }
                context.input.releaseKey(key)
                context.waitTicks(2)
                context.input.pressKey(key)
                context.waitTicks(2)
                check(!shown()) { "Toggle's press did not hide the grid" }
                context.input.pressKey(key)
                context.waitTicks(2)
                check(shown()) { "Toggle's second press did not show the grid" }
                general.inventoryKeyMode = KeyMode.HOLD
                context.waitTicks(2)
                check(!shown()) { "Under Hold the grid shows with the key up" }
                context.input.holdKey(key)
                context.waitTicks(2)
                check(shown()) { "Under Hold the grid does not show while the key is down" }
                context.input.releaseKey(key)
                context.waitTicks(2)
                check(!shown()) { "Under Hold the grid stays after the release" }
                general.inventoryKey = -1
                context.waitTicks(2)
                check(shown()) { "With no key the grid does not always show" }

                // The icon looks, for a player who wants the grid lighter than the inventory.
                for (look in listOf(InventoryLook.PLATE, InventoryLook.CLEAR)) {
                    general.inventoryLook = look
                    context.waitTicks(10)
                    check(skin(stackAt(cell(0, 0, inventory = false)))) { "The ${look.name} look moved the first item off its cell" }
                    context.takeScreenshot("inventory-preview-${look.name.lowercase()}")
                }
            } finally {
                general.inventoryPreview = false
                general.inventoryRows = mutableListOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM)
                general.inventoryLook = InventoryLook.INVENTORY
                general.inventoryPlacement = HudDock.FREE
                general.inventoryKey = -1
                general.inventoryKeyMode = KeyMode.TOGGLE
                context.runOnClient<Nothing> { it.player?.inventory?.selectedSlot = 0 }
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

    /** The color in [shot] at [dx], [dy] GUI pixels from [point], without its alpha. */
    private fun pixel(context: ClientGameTestContext, shot: Path, point: Pair<Double, Double>, dx: Int, dy: Int): Int {
        val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
        return ImageIO.read(shot.toFile()).getRGB(((point.first + dx) * scale).toInt(), ((point.second + dy) * scale).toInt()) and 0xFFFFFF
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
            // A diamond sword as dragsim.co marks a Legendary one, beside the plain ones at 20 and 25.
            15 -> ItemStack(Items.DIAMOND_SWORD).also {
                it.set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { put("item_data", CompoundTag().apply { putString("rarity", "LEGENDARY") }) }))
            }
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
