package io.github.noxaur.dsmod.gametest

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import io.github.noxaur.dsmod.core.camera.KeyMode
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
import net.minecraft.world.item.component.ResolvableProfile
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

/**
 * The world the Inventory HUD game tests share: an inventory of named items with lore as long as dragsim.co's, a
 * head with a skin, dyed leather, a stack of 13 and a Legendary sword, and the grid switched on over it.
 */
object InventoryGrid {
    const val ID = "inventory-hud"
    const val DYE = 0x3366CC

    /** The face of the inventory texture's panel. */
    const val VANILLA_PANEL = 0xC6C6C6

    /** The items in the three rows and the hotbar. */
    const val FILLED = 34

    private val ITEMS = listOf(Items.DIAMOND_SWORD, Items.OBSIDIAN, Items.BOW, Items.GOLDEN_HOE, Items.NETHER_STAR)

    /** Slots left empty: one in the top row, and one in the hotbar. */
    private val EMPTY = setOf(12, 4)

    private const val SKIN = "f83159c5bb7d80454d2d646b15774b7116aabcb3af5b6777a27573fc45f7854f"
    private val TEXTURES: String = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/$SKIN\"}}}".toByteArray())

    /** Fills the inventory, turns the grid on and runs [test] with it showing, then puts every setting back. */
    fun with(context: ClientGameTestContext, test: (TestSingleplayerContext) -> Unit) {
        val general = DsModClient.settings.general
        context.worldBuilder().create().use { world ->
            world.server.runOnServer<Nothing> { server ->
                val inventory = server.playerList.players.first().inventory
                // The player's inventory holds the hotbar in 0 to 8 and the three rows from 9.
                for (index in 0 until 36) inventory.setItem(index, item(index))
            }
            // The poller reads long-lored items over a few ticks, so the test starts once every one has arrived.
            context.waitFor { DsModClient.game.container.slots.let { slots -> slots.size > 44 && slots.subList(9, 45).count { it != null } == FILLED } }
            general.inventoryHud = true
            try {
                context.waitFor { Hud.find(ID) != null }
                test(world)
            } finally {
                general.inventoryHud = false
                general.inventoryRows = mutableListOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM)
                general.inventoryLook = InventoryLook.INVENTORY
                general.inventoryPlacement = HudDock.FREE
                general.inventoryKey = -1
                general.inventoryKeyMode = KeyMode.TOGGLE
                general.inventoryLayout = InventoryLayout.ROWS
                general.inventoryOpacity = 100f
                general.inventoryPickup = false
                context.runOnClient<Nothing> { it.player?.inventory?.selectedSlot = 0 }
            }
        }
    }

    /**
     * The middle of the cell in [row] and [column], in GUI pixels, on the plate or in the inventory's 18px slots 7px
     * into its panel, with [gaps] rows standing apart before it. In [columns] each row runs down.
     */
    fun cell(context: ClientGameTestContext, row: Int, column: Int, inventory: Boolean = true, gaps: Int = 0, columns: Boolean = false): Pair<Double, Double> =
        context.computeOnClient<Pair<Double, Double>, Nothing> {
            val block = checkNotNull(Hud.find(ID)) { "The inventory is not on the HUD" }
            val inset = if (inventory) 7 + 1 else 0
            val pitch = if (inventory) 18 else 17
            val along = column * pitch
            val between = row * pitch + gaps * 4
            val x = (4 + inset + (if (columns) between else along) + 8) * block.scale
            val y = (4 + inset + (if (columns) along else between) + 8) * block.scale
            block.x + x.toDouble() to block.y + y.toDouble()
        }

    fun stackAt(context: ClientGameTestContext, point: Pair<Double, Double>) =
        context.computeOnClient<ItemStack?, Nothing> { Hud.itemAt(point.first, point.second) }

    /** Waits until the grid draws the item named [name] at [point], as it does once a changed setting reaches the HUD. */
    fun waitForItem(context: ClientGameTestContext, name: String, point: () -> Pair<Double, Double>) {
        context.waitFor { Hud.find(ID) != null && point().let { p -> Hud.itemAt(p.first, p.second)?.hoverName?.string == name } }
    }

    /** The HUD's average draw over half a second, in µs a frame. */
    fun drawMicros(context: ClientGameTestContext): Long {
        context.runOnClient<Nothing> { DsModClient.game.metrics.flush() }
        context.waitTicks(10)
        return context.computeOnClient<Long, Nothing> {
            DsModClient.game.metrics.snapshot().timers.getValue("hud.draw").let { timer -> timer.totalNanos / timer.count / 1000 }
        }
    }

    /** The color in [shot] at [dx], [dy] GUI pixels from [point], without its alpha. */
    fun pixel(context: ClientGameTestContext, shot: Path, point: Pair<Double, Double>, dx: Int, dy: Int): Int {
        val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
        return ImageIO.read(shot.toFile()).getRGB(((point.first + dx) * scale).toInt(), ((point.second + dy) * scale).toInt()) and 0xFFFFFF
    }

    /** The color of the grid's panel in [shot], a few pixels in from its corner, between the edge and the first slot. */
    fun panelColor(context: ClientGameTestContext, shot: Path): Int {
        val (x, y) = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
            val block = checkNotNull(Hud.find(ID))
            val scale = client.window.guiScale
            ((block.x + (4 + 5) * block.scale) * scale).toInt() to ((block.y + (4 + 5) * block.scale) * scale).toInt()
        }
        return ImageIO.read(shot.toFile()).getRGB(x, y) and 0xFFFFFF
    }

    /** Whether [stack] wears the head's skin, which the HUD rebuilt from the mod's own record of the item. */
    fun skin(stack: ItemStack?): Boolean {
        val value = stack?.get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value() ?: return false
        return SKIN in String(Base64.getDecoder().decode(value))
    }

    /** The item for inventory [index], named after it and with lore as long as dragsim.co's, or none in a few slots. */
    fun item(index: Int): ItemStack {
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
}
