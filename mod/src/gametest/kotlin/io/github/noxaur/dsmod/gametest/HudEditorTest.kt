package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.HudEditorScreen
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitStepper
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import kotlin.io.path.readText
import kotlin.math.roundToInt

private const val ID = "dragon-timer"

/**
 * Opens the editor with nothing on the HUD, then over a live block. Moves and resizes the block, works every
 * control on the panel, drags the block under the panel and resets it from there, and checks the place is saved.
 */
class HudEditorTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            context.runOnClient<Nothing> { DsModClient.openHudEditor() }
            context.waitForScreen(HudEditorScreen::class.java)
            context.waitTicks(2)
            context.takeScreenshot("hud-editor-empty")
            context.setScreen { null }

            context.enterTheEnd()
            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            context.waitTicks(5)
            context.runOnClient<Nothing> { DsModClient.openHudEditor() }
            context.waitForScreen(HudEditorScreen::class.java)
            context.takeScreenshot("hud-editor-before")

            // The block's drawn scale, in screen pixels per HUD pixel.
            fun pixels(client: Minecraft) = (Hud.find(ID)!!.scale * client.window.guiScale).roundToInt()

            val start = context.computeOnClient<Int, Nothing> { client ->
                val screen = client.gui.screen()!!
                val block = Hud.find(ID)!!
                val start = pixels(client)
                check(screen.mouseClicked(press(block.x + 5.0, block.y + 5.0), false)) { "The block was not under the mouse" }
                screen.mouseDragged(press(block.x + 105.0, block.y + 65.0), 100.0, 60.0)
                screen.mouseReleased(press(block.x + 105.0, block.y + 65.0))
                check(screen.mouseScrolled(block.x + 110.0, block.y + 70.0, 0.0, 1.0)) { "The block did not scale" }
                check(pixels(client) == start + 1) { "A scroll step drew ${pixels(client)} pixels, not ${start + 1}" }
                start
            }
            val moved = DsModClient.settings.hud.positions.getValue(ID)
            check(moved.x > 0.05f && moved.y > 0.05f && moved.scale > 1f) { "Not moved and scaled: $moved" }

            // The panel works on the block clicked last: one step up by real mouse, on the stepper's plus.
            val plus = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
                val stepper = steppers(client).first()
                (stepper.right - 4.0) * client.window.guiScale to (stepper.y + 8.0) * client.window.guiScale
            }
            context.input.setCursorPos(plus.first, plus.second)
            context.input.pressMouse(0)
            context.waitTicks(2)
            context.runOnClient<Nothing> { check(pixels(it) == start + 2) { "The stepper drew ${pixels(it)} pixels, not ${start + 2}" } }

            context.runOnClient<Nothing> { client ->
                val hud = DsModClient.settings.hud
                val global = steppers(client).last()
                val before = hud.scale
                fun click(x: Double) {
                    check(global.mouseClicked(press(x, global.y + 8.0), false)) { "The HUD stepper was not under the mouse" }
                    global.mouseReleased(press(x, global.y + 8.0))
                }
                click(global.right - 4.0)
                check(hud.scale > before) { "The HUD scale did not grow: ${hud.scale}" }
                click(global.x + 4.0)
                check(hud.scale == before) { "The HUD scale did not come back: ${hud.scale}" }
            }
            val box = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
                val box = client.gui.screen()!!.children().filterIsInstance<KitCheckbox>().single()
                (box.x + 5.0) * client.window.guiScale to (box.y + 5.0) * client.window.guiScale
            }
            context.input.setCursorPos(box.first, box.second)
            context.input.pressMouse(0)
            context.waitTick()
            check(!DsModClient.settings.hud.background) { "The checkbox did not turn the background off" }
            context.input.pressMouse(0)
            context.waitTick()
            check(DsModClient.settings.hud.background) { "The checkbox did not turn the background on" }
            context.waitTicks(2)
            context.takeScreenshot("hud-editor-after")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTicks(2)
            context.takeScreenshot("hud-editor-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

            // A drag that starts on the block carries on over the panel, and Reset still reaches the block there.
            context.runOnClient<Nothing> { client ->
                val screen = client.gui.screen()!!
                val block = Hud.find(ID)!!
                val to = screen.width / 2.0 to screen.height - 30.0
                check(screen.mouseClicked(press(block.x + 5.0, block.y + 5.0), false)) { "The block was not under the mouse" }
                screen.mouseDragged(press(to.first, to.second), 0.0, 0.0)
                screen.mouseReleased(press(to.first, to.second))
                val under = Hud.find(ID)!!
                check(Math.abs(under.x + 5.0 - to.first) <= 1 && Math.abs(under.y + 5.0 - to.second) <= 1) { "The drag stopped at the panel: ${under.x}, ${under.y}" }
            }
            context.waitTicks(2)
            context.takeScreenshot("hud-editor-under-panel")
            context.clickScreenButton("Reset")
            check(ID !in DsModClient.settings.hud.positions) { "Reset kept the position" }


            // Let go three pixels off the middle of the screen, the block centers itself. With Shift it stays where it was dropped.
            context.runOnClient<Nothing> { client ->
                val screen = client.gui.screen()!!
                fun dragTo(x: Double, y: Double, modifiers: Int): Hud.Placed {
                    val block = Hud.find(ID)!!
                    fun at(px: Double, py: Double) = MouseButtonEvent(px, py, MouseButtonInfo(0, modifiers))
                    check(screen.mouseClicked(at(block.x + 5.0, block.y + 5.0), false)) { "The block was not under the mouse" }
                    screen.mouseDragged(at(x + 5.0, y + 5.0), 0.0, 0.0)
                    screen.mouseReleased(at(x + 5.0, y + 5.0))
                    return Hud.find(ID)!!
                }
                val size = Hud.find(ID)!!.let { it.width * it.scale to it.height * it.scale }
                val centered = (screen.width - size.first) / 2.0
                val middle = (screen.height - size.second) / 2.0
                val snapped = dragTo(centered + 3, middle - 3, 0)
                check(Math.abs(snapped.x - centered) < 0.6 && Math.abs(snapped.y - middle) < 0.6) { "The block did not center: ${snapped.x}, ${snapped.y}, wanted $centered, $middle on ${screen.width}x${screen.height} with $size" }
                val free = dragTo(centered + 3, middle - 3, org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT)
                check(Math.abs(free.x - (centered + 3)) < 0.6 && Math.abs(free.y - (middle - 3)) < 0.6) { "Shift did not skip the snap: ${free.x}, ${free.y}" }
            }

            context.runOnClient<Nothing> { client ->
                val screen = client.gui.screen()!!
                val block = Hud.find(ID)!!
                check(screen.mouseClicked(press(block.x + 5.0, block.y + 5.0), false)) { "The block was not under the mouse" }
                screen.mouseDragged(press(block.x + 105.0, block.y + 65.0), 100.0, 60.0)
                screen.mouseReleased(press(block.x + 105.0, block.y + 65.0))
            }
            val placed = DsModClient.settings.hud.positions.getValue(ID)
            context.clickScreenButton("Done")
            context.waitFor { it.gui.screen() == null }
            context.waitTicks(20)
            val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
            check(""""$ID":{"x":${placed.x},"y":${placed.y},"scale":${placed.scale}}""" in saved) { "The position was not saved: $saved" }
            context.takeScreenshot("hud-moved")
        }
    }

    private fun press(x: Double, y: Double) = MouseButtonEvent(x, y, MouseButtonInfo(0, 0))

    private fun steppers(client: Minecraft) = client.gui.screen()!!.children().filterIsInstance<KitStepper>()
}
