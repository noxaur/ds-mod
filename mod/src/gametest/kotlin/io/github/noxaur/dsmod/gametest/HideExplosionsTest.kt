package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.HideExplosions
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.particles.ParticleTypes

/**
 * The server sets off the same explosion twice. Without the hider the client makes its cloud, and with it the
 * client makes none, while another particle still appears. Read both screenshots.
 */
class HideExplosionsTest : FabricClientGameTest {
    private fun made(context: ClientGameTestContext, explosion: Boolean) = context.computeOnClient<Boolean, Nothing> { client ->
        val at = client.player!!.position()
        client.particleEngine.createParticle(if (explosion) ParticleTypes.EXPLOSION else ParticleTypes.FLAME, at.x, at.y + 1, at.z, 0.0, 0.0, 0.0) != null
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            context.waitTicks(20)
            fun explode(name: String) {
                world.server.runCommand("execute at @p run particle minecraft:explosion_emitter ^ ^1 ^6 0 0 0 0 1 force")
                context.waitTicks(6)
                context.takeScreenshot(name)
                context.waitTicks(40)
            }
            check(made(context, explosion = true)) { "The client made no explosion with the hider off" }
            explode("explosion-shown")
            try {
                DsModClient.feature<HideExplosions>().feature.enabled = true
                check(!made(context, explosion = true)) { "The client still made an explosion" }
                check(made(context, explosion = false)) { "The hider stopped a flame too" }
                explode("explosion-hidden")
            } finally {
                DsModClient.feature<HideExplosions>().feature.enabled = false
            }
        }
    }
}
