package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.BetterUi
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.commands.Commands

class BetterUiTest : FabricClientGameTest {
    private data class Shown(val goBack: Boolean, val tabs: Boolean)

    private fun shown(context: ClientGameTestContext) = context.computeOnClient<Shown, Nothing> { client ->
        val screen = client.gui.screen() as? ContainerScreen ?: return@computeOnClient Shown(goBack = false, tabs = false)
        val widgets = Screens.getWidgets(screen).filter { it.visible }
        Shown(widgets.any { it is KitButton && it.message.string == "Go Back" }, widgets.any { it is KitTab })
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.server.runOnServer<Nothing> { server ->
                server.commands.dispatcher.register(
                    Commands.literal("storage").executes { it.source.playerOrException.openMenu(ServerMenus.menu(6, "Storage", goBack = false)); 1 },
                )
            }
            context.runOnClient<Nothing> { it.connection!!.sendCommand("storage") }
            ServerMenus.shows(context, "Storage")
            ServerMenus.open(context, world, 4, "Backpack Slot 1", goBack = false)
            val both = Shown(goBack = true, tabs = true)
            check(shown(context) == both) { "With Better UI on the menu shows ${shown(context)}" }
            try {
                context.runOnClient<Nothing> { DsModClient.feature<BetterUi>().feature.enabled = false }
                context.waitTicks(2)
                check(shown(context) == Shown(goBack = false, tabs = false)) { "With Better UI off the menu shows ${shown(context)}" }
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<BetterUi>().feature.enabled = true }
            }
            context.waitTicks(2)
            check(shown(context) == both) { "With Better UI on again the menu shows ${shown(context)}" }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
