package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.BackButtons
import io.github.noxaur.dsmod.core.feature.inventory.BetterUi
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.MenuTrail
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.KitButton
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.AbstractContainerMenu

/**
 * Back Buttons, under Better UI. A chest menu the server gave no Go Back gets a button where Go Back sits in the server's menus, in the
 * middle of the bottom row, left of Close. It sends the command that opened the last menu in the run, which
 * [MenuTrail] keeps. It shows only while that slot holds filler. An empty slot is one the player can put an item in,
 * as every slot of a storage page is, so it never covers a slot the player uses.
 */
object MenuBack {
    private const val GO_BACK = "Go Back"

    private val trail = MenuTrail()
    private var menu: AbstractContainerMenu? = null
    private var back: String? = null

    private val enabled get() = DsModClient.runs<BetterUi>() && DsModClient.runs<BackButtons>()

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
            SlotControls.add(screen, chest[chest.size - 6], { enabled && chest.none { it.item.plainName == GO_BACK } }) { x, y ->
                KitButton(x, y, Icon.BACK, GO_BACK) { send(command) }.apply {
                    setTooltip(Tooltip.create(text { body(GO_BACK); muted("\nSends /$command."); action("\nClick to go back.") }.toComponent()))
                    glass = { MenuSkin.glass(screen) >= 0 }
                }
            }
        }
    }

    /** The command that opened the menu of [screen], or null when none did or it is not the menu open now. */
    fun opener(screen: AbstractContainerScreen<*>) = if (screen.menu === menu) trail.opener else null

    /**
     * Sends [command] for the player, on their click on a Back or Command button, and lets the trail know, so the menu
     * it opens starts a run.
     */
    fun send(command: String) {
        val connection = Minecraft.getInstance().connection ?: return
        trail.sent(System.currentTimeMillis(), command)
        connection.sendCommand(command)
    }
}
