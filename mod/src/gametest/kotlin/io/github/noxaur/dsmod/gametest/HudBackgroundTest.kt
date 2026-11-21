package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.hud.HudBackground
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import javax.imageio.ImageIO

class HudBackgroundTest : FabricClientGameTest {
    private fun corner(context: ClientGameTestContext, name: String): Int {
        val shot = context.screenshot(name)
        val (x, y) = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
            val block = checkNotNull(Hud.find(TIMER)) { "The dragon timer is not on the HUD" }
            val scale = client.window.guiScale
            ((block.x + 2 * block.scale) * scale).toInt() to ((block.y + 2 * block.scale) * scale).toInt()
        }
        return ImageIO.read(shot.toFile()).getRGB(x, y) and 0xFFFFFF
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            context.enterTheEnd()
            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            context.waitTicks(5)
            val plated = corner(context, "hud-background-on")
            try {
                context.runOnClient<Nothing> { DsModClient.feature<HudBackground>().feature.enabled = false }
                context.waitTicks(2)
                val bare = corner(context, "hud-background-off")
                check(bare != plated) { "The corner of the dragon timer is ${"%06x".format(bare)} with HUD Background off, as with it on" }
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<HudBackground>().feature.enabled = true }
            }
            context.waitTicks(2)
            check(corner(context, "hud-background-again") == plated) { "The plate did not come back with HUD Background on" }
        }
    }

    private companion object {
        const val TIMER = "dragon-timer"
    }
}
