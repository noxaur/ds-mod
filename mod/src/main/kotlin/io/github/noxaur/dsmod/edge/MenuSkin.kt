package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.container.MenuButton
import io.github.noxaur.dsmod.core.ui.CursorMemory
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Menus
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.CraftingScreen
import net.minecraft.client.gui.screens.inventory.DispenserScreen
import net.minecraft.client.gui.screens.inventory.HopperScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ResultSlot
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * Themed Menus, Stable Menus and Keep Cursor, which the mixins in `edge/mixin` ask. A themed menu is drawn by the kit in
 * place of its texture: a panel, a slot under each of its slots, and the well the inventory shows the player in.
 * Only the screens whose texture is nothing more than that are themed. A furnace or an anvil keeps its own art,
 * because its arrows and fields live in the texture, and the server opens chests. The recipe book's button on a themed
 * menu is a kit button too, and so are the server's own [MenuButton]s, so they look like the Go Back the mod adds.
 * Only their drawing changes: a click on one still reaches its slot. Under the Glass look the panel, slots and tabs are
 * glass instead, and the screen leaves the world and the HUD behind them undimmed, save the crosshair, which would sit
 * in the middle of the menu as a stray mark.
 */
object MenuSkin {
    private val settings get() = DsModClient.settings.inventory

    val cursor = CursorMemory()

    val themed: Boolean get() = DsModClient.active && settings.themedMenus

    val stable: Boolean get() = DsModClient.active && settings.stableMenus

    val keepsCursor: Boolean get() = DsModClient.active && settings.keepCursor

    private var buttonMenu: AbstractContainerMenu? = null
    private var buttonStacks = arrayOfNulls<ItemStack>(0)
    private var buttons = arrayOfNulls<MenuButton>(0)

    fun register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR) { element ->
            HudElement { graphics, delta -> if (!glassOpen()) element.extractRenderState(graphics, delta) }
        }
        // Fabric drops a screen's listeners on every init, so they are added again here.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is ContainerScreen) ScreenEvents.afterTick(screen).register { findButtons(screen.menu) }
        }
    }

    /** Looks again only at a chest slot whose stack changed, because the server fills a menu over a few ticks. */
    private fun findButtons(menu: ChestMenu) {
        val size = menu.rowCount * 9
        if (menu !== buttonMenu) {
            buttonMenu = menu
            buttonStacks = arrayOfNulls(size)
            buttons = arrayOfNulls(size)
        }
        for (i in 0 until size) {
            val stack = menu.slots[i].item
            if (stack === buttonStacks[i]) continue
            buttonStacks[i] = stack
            buttons[i] = if (stack.isEmpty) null else MenuButton.of(BuiltInRegistries.ITEM.getKey(stack.item).toString(), stack.get(DataComponents.CUSTOM_NAME)?.string)
        }
    }

    /** The server's button in slot [index] of [screen]'s menu when the kit draws it, or null. */
    fun button(screen: AbstractContainerScreen<*>, index: Int): MenuButton? =
        if (screen.menu === buttonMenu && skins(screen)) buttons.getOrNull(index) else null

    /** Draws [button] in place of [slot]'s item, the size and place of the Go Back the mod adds. [graphics] is at the menu's corner. */
    fun drawButton(graphics: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>, slot: Slot, button: MenuButton) {
        val hovered = (screen as ContainerScreenAccessor).hoveredSlot === slot
        val icon = when (button) {
            MenuButton.BACK -> Icon.BACK
            MenuButton.CLOSE -> Icon.CROSS
            MenuButton.NEXT -> Icon.RIGHT
            MenuButton.PREVIOUS -> Icon.LEFT
        }
        val state = Kit.state(true, hovered, hovered && Minecraft.getInstance().mouseHandler.isLeftPressed)
        Kit.button(graphics, slot.x, slot.y, Kit.BUTTON_HEIGHT, Kit.BUTTON_HEIGHT, state, icon, glass(screen) >= 0)
    }

    /** Whether the kit draws [screen] in place of its texture, and its labels in the theme's ink. */
    fun skins(screen: AbstractContainerScreen<*>) = themed && (
        screen is ContainerScreen || screen is InventoryScreen || screen is CraftingScreen ||
            screen is HopperScreen || screen is DispenserScreen || screen is ShulkerBoxScreen
        )

    /** The alpha of the glass themed menus are drawn as, from 0 for clear to 255, or -1 when they are solid or not themed. */
    val glassAlpha: Int get() = if (themed && settings.menuLook == MenuLook.GLASS) Menus.glassAlpha(settings.glassOpacity) else -1

    /** The alpha of [screen]'s glass, from 0 for clear to 255, or -1 when the kit does not draw it as glass. */
    fun glass(screen: AbstractContainerScreen<*>): Int = if (skins(screen)) glassAlpha else -1

    /** Whether the menu open now is drawn as glass. */
    private fun glassOpen(): Boolean {
        val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*> ?: return false
        return glass(screen) >= 0
    }

    fun background(graphics: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) = DsModClient.game.metrics.time("menus.draw") {
        val glass = glass(screen)
        val solid = glass < 0
        screen as ContainerScreenAccessor
        val left = screen.leftPos
        val top = screen.topPos
        if (solid) Kit.panel(graphics, left, top, screen.imageWidth, screen.imageHeight) else Kit.glass(graphics, left, top, screen.imageWidth, screen.imageHeight, glass)
        // The vanilla texture keeps a dark box behind the player, where the inventory screen draws them.
        if (screen is InventoryScreen) {
            if (solid) Kit.well(graphics, left + 25, top + 7, 51, 72) else Kit.glassWell(graphics, left + 25, top + 7, 51, 72)
        }
        val slots = screen.menu.slots
        var result: ResultSlot? = null
        for (slot in slots) {
            if (!slot.isActive) continue
            if (slot is ResultSlot) {
                result = slot
                val x = left + slot.x - 5
                val y = top + slot.y - 5
                if (solid) Kit.slot(graphics, x, y, Kit.RESULT_SLOT_SIZE) else Kit.glassSlot(graphics, x, y, Kit.RESULT_SLOT_SIZE)
            } else if (!Slots.hides(slot.index) && button(screen, slot.index) == null) {
                if (solid) Kit.slot(graphics, left + slot.x - 1, top + slot.y - 1) else Kit.glassSlot(graphics, left + slot.x - 1, top + slot.y - 1)
            }
        }
        result ?: return@time
        // The arrow sits halfway between the crafting grid and the result, on the result's middle.
        var gridRight = 0
        for (slot in slots) if (slot.x < result.x && slot.y in result.y - 17..result.y + 17) gridRight = maxOf(gridRight, slot.x + 17)
        val icon = Icon.ARROW
        val x = left + (gridRight + result.x - 5 - icon.width) / 2
        Kit.icon(graphics, icon, x, top + result.y + (16 - icon.height) / 2, ink(glass), inkShadow(glass))
    }

    /** The ink of a menu's labels and icons: light with its shadow on [glass], or the theme's on a solid panel. */
    fun ink(glass: Int) = if (glass < 0) Kit.theme.ink else Kit.GLASS_INK

    fun inkShadow(glass: Int) = glass >= 0 || Kit.theme.inkShadow

    /** Draws the recipe book's [button] with the kit when the open menu is themed, or returns false to leave it vanilla. */
    fun recipeButton(graphics: GuiGraphicsExtractor, button: AbstractWidget): Boolean {
        val client = Minecraft.getInstance()
        val screen = client.gui.screen() as? AbstractContainerScreen<*> ?: return false
        if (!skins(screen)) return false
        val pressed = button.isHovered && client.mouseHandler.isLeftPressed
        Kit.button(graphics, button.x, button.y, button.width, button.height, Kit.state(button.active, button.isHoveredOrFocused, pressed), Icon.BOOK, glass(screen) >= 0)
        return true
    }
}
