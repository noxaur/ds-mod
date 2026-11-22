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
        fun heldUses(walk: Boolean, message: (Int) -> String) = context.autoClicks(walk = walk, key = key).let { (_, uses) -> check(uses == 0) { message(uses) } }
        context.autoClickWorld {
            try {
                right.click.key.value = key
                heldUses(walk = true) { "With Auto Right Click off the held key used $it times" }
                context.ownClicks(1).let { (_, uses) -> check(uses == 20) { "With Auto Right Click off ClickCapMixin let $uses of 20 right clicks through" } }

                right.feature.enabled = true
                context.autoClicks(walk = true, key = key).let { (attacks, uses) -> check(attacks == 0 && uses in 9..11) { "AutoClicks used $uses and attacked $attacks times in a second of walking with the key held" } }
                heldUses(walk = false) { "AutoClicks used $it times standing still" }
                context.ownClicks(1).let { (_, uses) -> check(uses in 9..11) { "ClickCapMixin let $uses of 20 right clicks in a second through" } }
            } finally {
                right.feature.enabled = false
                right.click.key.value = 1
            }
        }
    }
}
