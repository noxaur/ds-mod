package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.AutoRightClick
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

class AutoRightClickTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val right = DsModClient.feature<AutoRightClick>()
        val key = GLFW.GLFW_KEY_K
        context.autoClickWorld {
            try {
                right.click.key.value = key
                context.autoClicks(walk = true, key = key).let { (_, uses) -> check(uses == 0) { "With Auto Right Click off the held key used $uses times" } }

                right.feature.enabled = true
                context.autoClicks(walk = true, key = key).let { (attacks, uses) -> check(attacks == 0 && uses in 9..11) { "AutoClicks used $uses and attacked $attacks times in a second of walking with the key held" } }
                context.autoClicks(walk = false, key = key).let { (_, uses) -> check(uses == 0) { "AutoClicks used $uses times standing still" } }
            } finally {
                right.feature.enabled = false
                right.click.key.value = 1
            }
        }
    }
}
