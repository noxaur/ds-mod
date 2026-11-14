package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.HudEditorScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import kotlin.io.path.readText

/** Opens the editor with nothing on the HUD, then over a live block, then moves and resizes the block and checks it is saved. */
class HudEditorTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            context.runOnClient<Nothing> { DsModClient.openHudEditor() }
            context.waitForScreen(HudEditorScreen::class.java)
            context.waitTicks(2)
            context.takeScreenshot("hud-editor-empty")
            context.setScreen { null }

            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            context.waitTicks(5)
            context.runOnClient<Nothing> { DsModClient.openHudEditor() }
            context.waitForScreen(HudEditorScreen::class.java)
            context.takeScreenshot("hud-editor-before")

            context.runOnClient<Nothing> { client ->
                val screen = client.gui.screen()!!
                val grab = MouseButtonEvent(6.0, 6.0, MouseButtonInfo(0, 0))
                check(screen.mouseClicked(grab, false)) { "The block was not under the mouse" }
                screen.mouseDragged(MouseButtonEvent(106.0, 66.0, MouseButtonInfo(0, 0)), 100.0, 60.0)
                screen.mouseReleased(MouseButtonEvent(106.0, 66.0, MouseButtonInfo(0, 0)))
                check(screen.mouseScrolled(110.0, 70.0, 0.0, 5.0)) { "The block did not scale" }
            }
            context.waitTicks(2)
            val moved = DsModClient.settings.hud.positions.getValue("dragon-timer")
            check(moved.x > 0.05f && moved.y > 0.05f && moved.scale > 1.4f) { "Not moved and scaled: $moved" }
            context.takeScreenshot("hud-editor-after")

            context.setScreen { null }
            context.waitTicks(20)
            val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
            check(""""dragon-timer":{"x":${moved.x},"y":${moved.y},"scale":${moved.scale}}""" in saved) { "The position was not saved: $saved" }
            context.takeScreenshot("hud-moved")
        }
    }
}
