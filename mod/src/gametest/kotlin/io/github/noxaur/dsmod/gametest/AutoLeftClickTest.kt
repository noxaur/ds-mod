package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.feature.general.AutoLeftClick
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW

class AutoLeftClickTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val left = DsModClient.feature<AutoLeftClick>()
        val key = GLFW.GLFW_KEY_J
        context.autoClickWorld {
            try {
                left.click.key.value = key
                context.autoClicks(walk = true, key = key).let { (attacks, _) -> check(attacks == 0) { "With Auto Left Click off the held key attacked $attacks times" } }
                context.ownClicks(0).let { (attacks, _) -> check(attacks == 20) { "With Auto Left Click off ClickCapMixin let $attacks of 20 left clicks through" } }

                left.feature.enabled = true
                context.autoClicks(walk = true, key = key).let { (attacks, uses) -> check(attacks in 9..11 && uses == 0) { "AutoClicks attacked $attacks and used $uses times in a second of walking with the key held" } }
                context.autoClicks(walk = false, key = key).let { (attacks, _) -> check(attacks == 0) { "AutoClicks attacked $attacks times standing still" } }
                context.ownClicks(0).let { (attacks, _) -> check(attacks in 9..11) { "ClickCapMixin let $attacks of 20 left clicks in a second through" } }

                left.click.key.value = 0
                left.click.mode.value = KeyMode.TOGGLE
                context.input.pressMouse(0)
                context.waitTicks(3)
                context.autoClicks(walk = true, key = null).let { (attacks, _) -> check(attacks in 9..11) { "A left click toggled AutoClicks to $attacks attacks" } }
                context.input.pressMouse(0)
                context.waitTicks(3)
                context.autoClicks(walk = true, key = null).let { (attacks, _) -> check(attacks == 0) { "A second left click left AutoClicks attacking $attacks times" } }

                context.input.pressMouse(0)
                context.waitTicks(3)
                context.runOnClient<Nothing> { it.gui.setScreen(ChatScreen("", false)) }
                context.waitForScreen(ChatScreen::class.java)
                context.autoClicks(walk = true, key = null).let { (attacks, _) -> check(attacks == 0) { "AutoClicks attacked $attacks times with the chat open" } }
            } finally {
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
                left.feature.enabled = false
                left.click.key.value = 0
                left.click.mode.value = KeyMode.HOLD
            }
        }
    }
}
