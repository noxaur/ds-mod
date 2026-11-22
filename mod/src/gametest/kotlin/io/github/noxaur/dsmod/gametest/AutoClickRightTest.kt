package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.AutoClick
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

/** @keep Covers [io.github.noxaur.dsmod.edge.mixin.KeyPressMixin]. */
class AutoClickRightTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val auto = DsModClient.feature<AutoClick>()
        val right = auto.right
        val key = GLFW.GLFW_KEY_K
        fun heldUses(walk: Boolean, message: (Int) -> String) = context.autoClicks(walk = walk, key = key).let { (_, uses) -> check(uses == 0) { message(uses) } }
        context.autoClickWorld {
            try {
                right.key.value = key
                heldUses(walk = true) { "With Auto Click off the held key used $it times" }
                context.ownClicks(1).let { (_, uses) -> check(uses == 20) { "With Auto Click off ClickCapMixin let $uses of 20 right clicks through" } }

                auto.feature.enabled = true
                context.autoClicks(walk = true, key = key).let { (attacks, uses) -> check(attacks == 0 && uses in 9..11) { "AutoClicks used $uses and attacked $attacks times in a second of walking with the key held" } }
                heldUses(walk = false) { "AutoClicks used $it times standing still" }
                context.ownClicks(1).let { (_, uses) -> check(uses in 9..11) { "ClickCapMixin let $uses of 20 right clicks in a second through" } }

                right.switch.value = false
                heldUses(walk = true) { "With Right Click off the held key used $it times" }
                context.ownClicks(1).let { (_, uses) -> check(uses == 20) { "With Right Click off ClickCapMixin let $uses of 20 right clicks through" } }
            } finally {
                auto.feature.enabled = false
                right.switch.value = true
                right.key.value = 1
            }
        }
    }
}
