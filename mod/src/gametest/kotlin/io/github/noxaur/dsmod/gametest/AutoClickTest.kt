package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.feature.general.AutoClick
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

class AutoClickTest : FabricClientGameTest {
    private fun attacked(clicks: Pair<Int, Int>, expected: IntRange, message: (Int) -> String) = check(clicks.first in expected) { message(clicks.first) }

    override fun runTest(context: ClientGameTestContext) {
        val auto = DsModClient.feature<AutoClick>()
        val left = auto.left
        val key = GLFW.GLFW_KEY_J
        context.autoClickWorld {
            try {
                left.key.value = key
                attacked(context.autoClicks(walk = true, key = key), 0..0) { "With Auto Click off the held key attacked $it times" }
                attacked(context.ownClicks(0), 20..20) { "With Auto Click off ClickCapMixin let $it of 20 left clicks through" }
                context.waitTicks(30)

                auto.feature.enabled = true
                context.autoClicks(walk = true, key = key).let { (attacks, uses) -> check(attacks in 9..11 && uses == 0) { "AutoClicks attacked $attacks and used $uses times in a second of walking with the key held" } }
                attacked(context.autoClicks(walk = false, key = key), 0..0) { "AutoClicks attacked $it times standing still" }
                attacked(context.ownClicks(0), 20..20) { "Auto Click dropped the player's own left clicks: $it of 20 went through" }
                context.waitTicks(30)

                left.key.value = 0
                left.mode.value = KeyMode.TOGGLE
                context.input.pressMouse(0)
                context.waitTicks(3)
                attacked(context.autoClicks(walk = true, key = null), 9..11) { "A left click toggled AutoClicks to $it attacks" }
                context.input.pressMouse(0)
                context.waitTicks(3)
                attacked(context.autoClicks(walk = true, key = null), 0..0) { "A second left click left AutoClicks attacking $it times" }

                context.input.pressMouse(0)
                context.waitTicks(3)
                context.openChat()
                attacked(context.autoClicks(walk = true, key = null), 0..0) { "AutoClicks attacked $it times with the chat open" }
            } finally {
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
                auto.feature.enabled = false
                left.key.value = 0
                left.mode.value = KeyMode.HOLD
            }
        }
    }
}
