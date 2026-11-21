package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.feature.general.AutoClicker
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW

class AutoClickerTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val clicker = DsModClient.feature<AutoClicker>()
        val leftKey = GLFW.GLFW_KEY_J
        val rightKey = GLFW.GLFW_KEY_K

        fun run(walk: Boolean, key: Int?): Pair<Int, Int> {
            context.runOnClient<Nothing> { ActionSpy.clear() }
            if (walk) context.input.holdKey { it.keyUp }
            if (key != null) context.input.holdKey(key)
            context.waitTicks(40)
            if (key != null) context.input.releaseKey(key)
            if (walk) context.input.releaseKey { it.keyUp }
            context.waitTicks(5)
            return context.computeOnClient<Pair<Int, Int>, Nothing> { ActionSpy.attacks to ActionSpy.uses }
        }

        context.worldBuilder().create().use { world ->
            world.server.runCommand("gamemode creative @a")
            world.server.runCommand("clear @a")
            context.waitTicks(40)
            context.input.lookAt(0f, -90f)
            context.waitTicks(3)
            try {
                clicker.left.key.value = leftKey
                clicker.right.key.value = rightKey
                run(walk = true, key = leftKey).let { (attacks, _) -> check(attacks == 0) { "With Auto Clicker off the held key attacked $attacks times" } }

                clicker.feature.enabled = true
                run(walk = true, key = leftKey).let { (attacks, uses) -> check(attacks in 19..21 && uses == 0) { "AutoClicks attacked $attacks and used $uses times in two seconds of walking with the left key held" } }
                run(walk = false, key = leftKey).let { (attacks, _) -> check(attacks == 0) { "AutoClicks attacked $attacks times standing still" } }
                run(walk = true, key = null).let { (attacks, uses) -> check(attacks == 0 && uses == 0) { "AutoClicks clicked $attacks and $uses times with no key held" } }
                run(walk = true, key = rightKey).let { (attacks, uses) -> check(attacks == 0 && uses in 19..21) { "AutoClicks used $uses and attacked $attacks times with the right key held" } }

                clicker.left.key.value = 0
                clicker.left.mode.value = KeyMode.TOGGLE
                context.input.pressMouse(0)
                context.waitTicks(3)
                run(walk = true, key = null).let { (attacks, _) -> check(attacks in 19..21) { "A left click toggled AutoClicks to $attacks attacks" } }
                context.input.pressMouse(0)
                context.waitTicks(3)
                run(walk = true, key = null).let { (attacks, _) -> check(attacks == 0) { "A second left click left AutoClicks attacking $attacks times" } }

                context.input.pressMouse(0)
                context.waitTicks(3)
                context.runOnClient<Nothing> { it.gui.setScreen(ChatScreen("", false)) }
                context.waitForScreen(ChatScreen::class.java)
                run(walk = true, key = null).let { (attacks, _) -> check(attacks == 0) { "AutoClicks attacked $attacks times with the chat open" } }
            } finally {
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
                clicker.feature.enabled = false
                clicker.left.key.value = 0
                clicker.right.key.value = 1
                clicker.left.mode.value = KeyMode.HOLD
            }
        }
    }
}
