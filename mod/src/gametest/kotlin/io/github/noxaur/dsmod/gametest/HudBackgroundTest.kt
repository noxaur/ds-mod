package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.hud.HudBackground
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.HudEditorScreen
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class HudBackgroundTest : FabricClientGameTest {
    private fun cornerBrightness(context: ClientGameTestContext, name: String): Int {
        val shot = context.screenshot(name)
        val block = context.computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(DRAGON_TIMER)) { "The dragon timer is not on the HUD" } }
        return brightness(context.guiPixel(shot, block.x + 2 * block.scale, block.y + 2 * block.scale))
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.freezeTime()
            context.showDragonTimer(world)
            context.waitTicks(5)
            val plated = cornerBrightness(context, "hud-background-on")
            val bare = try {
                context.runOnClient<Nothing> { DsModClient.feature<HudBackground>().feature.enabled = false }
                context.waitTicks(2)
                cornerBrightness(context, "hud-background-off")
            } finally {
                context.runOnClient<Nothing> { DsModClient.feature<HudBackground>().feature.enabled = true }
            }
            check(plated < bare - DARKER) { "The dragon timer's corner is $plated bright with HUD Background on and $bare with it off" }
            context.waitTicks(2)
            val again = cornerBrightness(context, "hud-background-again")
            check(again < bare - DARKER) { "The plate did not come back with HUD Background on: the corner is $again bright, and $bare bare" }

            try {
                context.runOnClient<Nothing> {
                    DsModClient.anyServer = false
                    DsModClient.openHudEditor()
                }
                context.waitForScreen(HudEditorScreen::class.java)
                val box = context.computeOnClient<KitCheckbox, Nothing> { checkNotNull(it.gui.screen()).widgets<KitCheckbox>().single() }
                check(context.computeOnClient<Boolean, Nothing> { !box.active }) { "The Background box can be clicked off dragsim.co, where the HUD draws no plate" }
                context.click(box.x + 5, box.y + 5)
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
        const val DARKER = 60
    }
}
