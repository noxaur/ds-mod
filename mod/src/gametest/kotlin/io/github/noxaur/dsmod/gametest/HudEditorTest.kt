package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.hud.HudBackground
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
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW
import kotlin.io.path.readText
import kotlin.math.roundToInt

class HudEditorTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            openEditor(context)
            context.waitTicks(2)
            context.takeScreenshot("hud-editor-empty")
            context.setScreen { null }

            context.showDragonTimer(world)
            context.waitTicks(5)
            openEditor(context)
            context.takeScreenshot("hud-editor-before")

            fun pixels(client: Minecraft) = (Hud.find(DRAGON_TIMER)!!.scale * client.window.guiScale).roundToInt()

            val start = onBlock(context) { client, screen, block ->
                val start = pixels(client)
                dragAway(screen, block)
                check(screen.mouseScrolled(block.x + 110.0, block.y + 70.0, 0.0, 1.0)) { "The block did not scale" }
                check(pixels(client) == start + 1) { "A scroll step drew ${pixels(client)} pixels, not ${start + 1}" }
                start
            }
            val moved = DsModClient.settings.hud.positions.getValue(DRAGON_TIMER)
            check(moved.x > 0.05f && moved.y > 0.05f && moved.scale > 1f) { "Not moved and scaled: $moved" }

            val plus = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
                val stepper = steppers(client).first()
                stepper.right - 4.0 to stepper.y + 8.0
            }
            context.click(plus.first, plus.second, settle = 2)
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
            val box = context.computeOnClient<KitCheckbox, Nothing> { client -> checkNotNull(client.gui.screen()).widgets<KitCheckbox>().single() }
            context.click(box.x + 5, box.y + 5)
            check(!DsModClient.feature<HudBackground>().feature.runs) { "The checkbox did not turn the background off" }
            context.input.pressMouse(0)
            context.waitTick()
            check(DsModClient.feature<HudBackground>().feature.runs) { "The checkbox did not turn the background on" }
            context.waitTicks(2)
            context.takeScreenshot("hud-editor-after")
            context.inTheme(Theme.VANILLA) { context.takeScreenshot("hud-editor-vanilla") }

            onBlock(context) { _, screen, block ->
                val to = screen.width / 2.0 to screen.height - block.height * block.scale - 2.0 + 5.0
                grab(screen, block)
                release(screen, to.first, to.second, 0.0, 0.0)
                val under = Hud.find(DRAGON_TIMER)!!
                check(Math.abs(under.x + 5.0 - to.first) <= 1 && Math.abs(under.y + 5.0 - to.second) <= 1) { "The drag stopped at the panel: ${under.x}, ${under.y}" }
            }
            context.waitTicks(2)
            context.takeScreenshot("hud-editor-under-panel")
            context.clickScreenButton("Reset")
            check(DRAGON_TIMER !in DsModClient.settings.hud.positions) { "Reset kept the position" }

            fun dragTo(screen: net.minecraft.client.gui.screens.Screen, x: Double, y: Double): Hud.Placed {
                val block = Hud.find(DRAGON_TIMER)!!
                grab(screen, block)
                release(screen, x + 5.0, y + 5.0, 0.0, 0.0)
                return Hud.find(DRAGON_TIMER)!!
            }
            val (centered, middle) = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
                val screen = client.gui.screen()!!
                val size = Hud.find(DRAGON_TIMER)!!.let { it.width * it.scale to it.height * it.scale }
                val centered = (screen.width - size.first) / 2.0
                val middle = (screen.height - size.second) / 2.0
                Hud.placed().filter { it.id != DRAGON_TIMER }.forEach { Hud.hide(it.id) }
                val snapped = dragTo(screen, centered + 3, middle - 3)
                check(Math.abs(snapped.x - centered) < 0.6 && Math.abs(snapped.y - middle) < 0.6) { "The block did not center: ${snapped.x}, ${snapped.y}, wanted $centered, $middle on ${screen.width}x${screen.height} with $size" }
                centered to middle
            }
            context.input.holdShift()
            context.runOnClient<Nothing> { client ->
                val free = dragTo(client.gui.screen()!!, centered + 3, middle - 3)
                check(Math.abs(free.x - (centered + 3)) < 0.6 && Math.abs(free.y - (middle - 3)) < 0.6) { "Shift did not skip the snap: ${free.x}, ${free.y}" }
            }
            context.input.releaseShift()

            onBlock(context) { client, screen, before ->
                grab(screen, before)
                screen.mouseDragged(press(before.x + 45.0, before.y + 35.0), 40.0, 30.0)
                check(screen.keyPressed(net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0))) { "Escape was not taken" }
                screen.mouseReleased(press(before.x + 45.0, before.y + 35.0))
                val after = Hud.find(DRAGON_TIMER)!!
                check(after.x == before.x && after.y == before.y && client.gui.screen() === screen) { "Escape left the block at ${after.x}, ${after.y}, not ${before.x}, ${before.y}" }
            }

            val picked = context.computeOnClient<Pair<Float, Float>, Nothing> { Hud.find(DRAGON_TIMER)!!.let { it.x to it.y } }
            context.input.pressKey(GLFW.GLFW_KEY_LEFT)
            context.input.holdShift()
            context.input.pressKey(GLFW.GLFW_KEY_UP)
            context.input.releaseShift()
            context.waitTick()
            context.runOnClient<Nothing> {
                val block = Hud.find(DRAGON_TIMER)!!
                check(block.x == picked.first - 1 && block.y == picked.second - 10) { "The arrows put the block at ${block.x}, ${block.y}, not ${picked.first - 1}, ${picked.second - 10}" }
            }

            onBlock(context) { _, screen, block ->
                check(screen.mouseClicked(MouseButtonEvent(block.x + 5.0, block.y + 5.0, MouseButtonInfo(1, 0)), false)) { "The right press was not taken" }
                screen.mouseReleased(MouseButtonEvent(block.x + 5.0, block.y + 5.0, MouseButtonInfo(1, 0)))
                check(DRAGON_TIMER !in DsModClient.settings.hud.positions) { "The right press kept the position" }
            }

            onBlock(context) { _, screen, block -> dragAway(screen, block) }
            val placed = DsModClient.settings.hud.positions.getValue(DRAGON_TIMER)
            context.clickScreenButton("Done")
            context.waitFor { it.gui.screen() == null }
            context.waitTicks(20)
            val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
            check(""""$DRAGON_TIMER":{"x":${placed.x},"y":${placed.y},"scale":${placed.scale}}""" in saved) { "The position was not saved: $saved" }
            context.takeScreenshot("hud-moved")
        }
    }

    private fun openEditor(context: ClientGameTestContext) {
        context.runOnClient<Nothing> { DsModClient.openHudEditor() }
        context.waitForScreen(HudEditorScreen::class.java)
    }

    private fun <T> onBlock(context: ClientGameTestContext, act: (Minecraft, Screen, Hud.Placed) -> T) =
        context.computeOnClient<T, Nothing> { client -> act(client, client.gui.screen()!!, Hud.find(DRAGON_TIMER)!!) }

    private fun grab(screen: Screen, block: Hud.Placed) {
        check(screen.mouseClicked(press(block.x + 5.0, block.y + 5.0), false)) { "The block was not under the mouse" }
    }

    private fun release(screen: Screen, x: Double, y: Double, dx: Double, dy: Double) {
        screen.mouseDragged(press(x, y), dx, dy)
        screen.mouseReleased(press(x, y))
    }

    private fun dragAway(screen: Screen, block: Hud.Placed) {
        grab(screen, block)
        release(screen, block.x + 105.0, block.y + 65.0, 100.0, 60.0)
    }

    private fun press(x: Double, y: Double) = MouseButtonEvent(x, y, MouseButtonInfo(0, 0))

    private fun steppers(client: Minecraft) = checkNotNull(client.gui.screen()).widgets<KitStepper>()
}
