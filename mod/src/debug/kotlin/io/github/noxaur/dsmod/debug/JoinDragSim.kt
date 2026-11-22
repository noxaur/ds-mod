package io.github.noxaur.dsmod.debug

import io.github.noxaur.dsmod.edge.ScreenHooks
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents

object JoinDragSim {
    const val LABEL = "Join DragSim"
    private const val ADDRESS = "dragsim.co"

    fun register() {
        ScreenHooks.onEveryLayout<TitleScreen> { screen, _ ->
            val buttons = Screens.getWidgets(screen)
            val singleplayer = buttons.firstOrNull { (it.message.contents as? TranslatableContents)?.key == "menu.singleplayer" }
                ?: return@onEveryLayout
            buttons += Button.builder(Component.literal(LABEL)) {
                // @keep Enabled skips the resource pack prompt, and the pack runClient seeds is already on disk.
                val server = ServerData("DragSim", ADDRESS, ServerData.Type.OTHER)
                    .apply { resourcePackStatus = ServerData.ServerPackStatus.ENABLED }
                ConnectScreen.startConnecting(screen, Minecraft.getInstance(), ServerAddress.parseString(ADDRESS), server, false, null)
            }.bounds(singleplayer.x, singleplayer.y - 24, singleplayer.width, singleplayer.height).build()
        }
    }
}
