package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.container.Filler
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.MenuTrail
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.KitButton
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

/**
 * Back Buttons. A chest menu the server gave no Go Back gets a button where Go Back sits in the server's menus, in the
 * middle of the bottom row, left of Close. It sends the command that opened the last menu in the run, which
 * [MenuTrail] keeps. It shows only while that slot is empty or filler, so it never covers an item that does something.
 */
object MenuBack {
    private const val GO_BACK = "Go Back"

    private val trail = MenuTrail()
    private var menu: AbstractContainerMenu? = null
    private var back: String? = null

    private val enabled get() = DsModClient.active && DsModClient.settings.inventory.backButtons

    fun register() {
        ClientSendMessageEvents.COMMAND.register { trail.sent(System.currentTimeMillis(), it) }
        // Fabric drops a screen's listeners on every init, and a resize inits the same menu again.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*> || screen is InventoryScreen || screen is CreativeModeInventoryScreen) return@register
            if (screen.menu !== menu) {
                menu = screen.menu
                back = trail.opened(System.currentTimeMillis(), screen.title.string)
            }
            ScreenEvents.remove(screen).register { trail.closed(System.currentTimeMillis()) }
            val command = back ?: return@register
            if (screen !is ContainerScreen) return@register
            val chest = screen.menu.slots.subList(0, screen.menu.rowCount * 9)
            val slot = chest[chest.size - 6]
            screen as ContainerScreenAccessor
            val button = KitButton(screen.leftPos + slot.x, screen.topPos + slot.y, Icon.BACK, GO_BACK) { press(command) }
            button.setTooltip(Tooltip.create(text { body(GO_BACK); muted("\nSends /$command."); action("\nClick to go back.") }.toComponent()))
            button.visible = false
            Screens.getWidgets(screen).add(button)
            ScreenEvents.afterTick(screen).register {
                // The server fills a menu's slots over a few ticks, and can put its own Go Back in later.
                button.visible = enabled && fits(slot.item) && chest.none { named(it.item) }
                Slots.cover(if (button.visible) slot.index else -1)
            }
        }
    }

    private fun fits(stack: ItemStack) = stack.isEmpty || Filler.of(BuiltInRegistries.ITEM.getKey(stack.item).toString(), stack.get(DataComponents.CUSTOM_NAME)?.string)

    private fun named(stack: ItemStack) = stack.get(DataComponents.CUSTOM_NAME)?.string == GO_BACK

    private fun press(command: String) {
        val connection = Minecraft.getInstance().connection ?: return
        trail.sent(System.currentTimeMillis(), command)
        connection.sendCommand(command) // rule:no-automation Menu Button: one command the player already sent, on their click
    }
}
