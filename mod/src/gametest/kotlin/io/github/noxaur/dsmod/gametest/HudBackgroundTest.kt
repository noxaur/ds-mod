package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.hud.HudBackground
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.HudEditorScreen
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import javax.imageio.ImageIO

class HudBackgroundTest : FabricClientGameTest {
    private fun brightness(context: ClientGameTestContext, name: String): Int {
        val shot = context.screenshot(name)
        val (x, y) = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
            val block = checkNotNull(Hud.find(TIMER)) { "The dragon timer is not on the HUD" }
            val scale = client.window.guiScale
            ((block.x + 2 * block.scale) * scale).toInt() to ((block.y + 2 * block.scale) * scale).toInt()
        }
        val rgb = ImageIO.read(shot.toFile()).getRGB(x, y)
        return (rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            listOf("gamerule advance_time false", "gamerule advance_weather false", "time set noon").forEach { world.server.runCommand(it) }
            context.enterTheEnd()
            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            context.waitTicks(5)
            val plated = brightness(context, "hud-background-on")
            val bare = try {
                context.runOnClient<Nothing> { DsModClient.feature<HudBackground>().feature.enabled = false }
                context.waitTicks(2)
                brightness(context, "hud-background-off")
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<HudBackground>().feature.enabled = true }
            }
            check(plated < bare - DARKER) { "The dragon timer's corner is $plated bright with HUD Background on and $bare with it off" }
            context.waitTicks(2)
            val again = brightness(context, "hud-background-again")
            check(again < bare - DARKER) { "The plate did not come back with HUD Background on: the corner is $again bright, and $bare bare" }

            try {
                context.runOnClient<Nothing> {
                    DsModClient.anyServer = false
                    DsModClient.openHudEditor()
                }
                context.waitForScreen(HudEditorScreen::class.java)
                val box = context.computeOnClient<KitCheckbox, Nothing> { it.gui.screen()!!.children().filterIsInstance<KitCheckbox>().single() }
                check(context.computeOnClient<Boolean, Nothing> { !box.active }) { "The Background box can be clicked off dragsim.co, where the HUD draws no plate" }
                val at = context.computeOnClient<Pair<Double, Double>, Nothing> { (box.x + 5.0) * it.window.guiScale to (box.y + 5.0) * it.window.guiScale }
                context.input.setCursorPos(at.first, at.second)
                context.input.pressMouse(0)
                context.waitTick()
                check(context.computeOnClient<Boolean, Nothing> { DsModClient.feature<HudBackground>().feature.enabled }) {
                    "A click on the disabled Background box turned HUD Background off"
                }
            } finally {
                context.runOnClient<Nothing> { DsModClient.anyServer = true }
                context.setScreen { null }
            }
        }
    }

    private companion object {
        const val TIMER = "dragon-timer"
        const val DARKER = 60
    }
}
