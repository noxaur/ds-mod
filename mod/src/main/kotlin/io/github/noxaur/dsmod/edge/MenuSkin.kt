package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.ui.CursorMemory
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.CraftingScreen
import net.minecraft.client.gui.screens.inventory.DispenserScreen
import net.minecraft.client.gui.screens.inventory.HopperScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.world.inventory.ResultSlot

/**
 * Themed Menus, Stable Menus and Keep Cursor, which the mixins in `edge/mixin` ask. A themed menu is drawn by the kit in
 * place of its texture: a panel, a slot under each of its slots, and the well the inventory shows the player in.
 * Only the screens whose texture is nothing more than that are themed. A furnace or an anvil keeps its own art,
 * because its arrows and fields live in the texture, and the server opens chests.
 */
object MenuSkin {
    private val settings get() = DsModClient.settings.inventory

    val cursor = CursorMemory()

    val themed: Boolean get() = DsModClient.active && settings.themedMenus

    val stable: Boolean get() = DsModClient.active && settings.stableMenus

    val keepsCursor: Boolean get() = DsModClient.active && settings.keepCursor

    /** Whether the kit draws [screen] in place of its texture, and its labels in the theme's ink. */
    fun skins(screen: AbstractContainerScreen<*>) = themed && (
        screen is ContainerScreen || screen is InventoryScreen || screen is CraftingScreen ||
            screen is HopperScreen || screen is DispenserScreen || screen is ShulkerBoxScreen
        )

    fun background(graphics: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) = DsModClient.game.metrics.time("menus.draw") {
        screen as ContainerScreenAccessor
        val left = screen.leftPos
        val top = screen.topPos
        Kit.panel(graphics, left, top, screen.imageWidth, screen.imageHeight)
        // The vanilla texture keeps a dark box behind the player, where the inventory screen draws them.
        if (screen is InventoryScreen) Kit.well(graphics, left + 25, top + 7, 51, 72)
        val slots = screen.menu.slots
        var result: ResultSlot? = null
        for (slot in slots) {
            if (!slot.isActive) continue
            if (slot is ResultSlot) {
                result = slot
                Kit.slot(graphics, left + slot.x - 5, top + slot.y - 5, Kit.RESULT_SLOT_SIZE)
            } else if (!Slots.hides(slot.index)) {
                Kit.slot(graphics, left + slot.x - 1, top + slot.y - 1)
            }
        }
        result ?: return@time
        // The arrow sits halfway between the crafting grid and the result, on the result's middle.
        var gridRight = 0
        for (slot in slots) if (slot.x < result.x && slot.y in result.y - 17..result.y + 17) gridRight = maxOf(gridRight, slot.x + 17)
        val icon = Icon.ARROW
        val x = left + (gridRight + result.x - 5 - icon.width) / 2
        Kit.icon(graphics, icon, x, top + result.y + (16 - icon.height) / 2, Kit.theme.ink, Kit.theme.inkShadow)
    }
}
