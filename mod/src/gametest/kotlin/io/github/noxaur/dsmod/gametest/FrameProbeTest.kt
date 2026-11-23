package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.debug.SettingsGalleryScreen
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class FrameProbeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = KitTest.inKit(context) {
        fun time(what: String) {
            context.waitTicks(5)
            val at = System.nanoTime()
            context.waitTicks(40)
            println("FrameProbe: $what %.0f ms a tick".format((System.nanoTime() - at) / 1e6 / 40))
        }
        context.setScreen { null }
        time("no screen")
        context.setScreen { SettingsGalleryScreen(DsModClient.settings.hud) }
        time("gallery, blur")
        context.runOnClient<Nothing> { it.options.menuBackgroundBlurriness().set(0) }
        time("gallery, no blur")
        context.runOnClient<Nothing> { it.options.menuBackgroundBlurriness().set(5) }
        context.setScreen { KitGalleryScreenProbe() }
        time("empty screen, blur")
        context.runOnClient<Nothing> { it.options.menuBackgroundBlurriness().set(0) }
        time("empty screen, no blur")
    }
}

private class KitGalleryScreenProbe : net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty())
