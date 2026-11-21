package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.BackButtons
import io.github.noxaur.dsmod.core.feature.inventory.BetterUi
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.MenuTrail
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.KitButton
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.ContainerScreen

/**
 * Back Buttons, under Better UI. A chest menu the server gave no Go Back gets a button where Go Back sits in the server's menus, in the
 * middle of the bottom row, left of Close. It sends the command that opened the last menu in the run, which
 * [MenuTrail] keeps. It shows only while that slot holds filler. An empty slot is one the player can put an item in,
 * as every slot of a storage page is, so it never covers a slot the player uses.
 */
object MenuBack {
    private const val GO_BACK = "Go Back"

    private val enabled get() = DsModClient.runs<BetterUi>() && DsModClient.runs<BackButtons>()

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is ContainerScreen) return@register
            val command = Trail.back(screen) ?: return@register
            val chest = screen.menu.slots.subList(0, screen.menu.rowCount * 9)
            SlotControls.add(screen, chest[chest.size - 6], { enabled && chest.none { it.item.plainName == GO_BACK } }) { x, y ->
                KitButton(x, y, Icon.BACK, GO_BACK) { PlayerCommands.send(command) }.apply {
                    setTooltip(Tooltip.create(text { body(GO_BACK); muted("\nSends /$command."); action("\nClick to go back.") }.toComponent()))
                    glass = { MenuSkin.glass(screen) >= 0 }
                }
            }
        }
    }
}
