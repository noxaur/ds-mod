package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.Fullbright
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import java.nio.file.Path

/** At midnight the ground is dark, and with Fullbright on it is as bright as at noon. Read both screenshots. */
class FullbrightTest : FabricClientGameTest {
    /** The mean brightness of the lower half of the image, where the ground is, from 0 to 255. */
    private fun ground(screenshot: Path): Double {
        val image = image(screenshot)
        var sum = 0L
        for (y in image.height / 2 until image.height) for (x in 0 until image.width) sum += brightness(image.getRGB(x, y))
        return sum / 3.0 / (image.width * (image.height - image.height / 2))
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.server.runCommand("time set midnight")
            context.waitTicks(40)
            val dark = ground(context.takeScreenshot("fullbright-off"))
            try {
                DsModClient.feature<Fullbright>().feature.enabled = true
                context.waitTicks(10)
                val lit = ground(context.takeScreenshot("fullbright-on"))
                check(lit > dark * 2) { "The ground went from $dark to $lit, which is not fully lit" }
            } finally {
                DsModClient.feature<Fullbright>().feature.enabled = false
            }
        }
    }
}
