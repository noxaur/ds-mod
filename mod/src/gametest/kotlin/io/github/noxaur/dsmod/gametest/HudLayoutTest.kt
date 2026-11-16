package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.HudEditorScreen
import io.github.noxaur.dsmod.edge.HudLayoutEditor
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.guiScale
import io.github.noxaur.dsmod.gametest.SettingsSteps.theme
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

private const val ID = "dragon-timer"

/**
 * Drives the HUD's layout in the settings over a live block: a drag moves it and keeps it on the screen, Escape
 * during a drag puts it back, the wheel and + resize it, the arrows nudge it, Backspace stacks it again, a right
 * press stacks it again, and Move HUD opens the full editor over the game, which returns to the settings. Read the
 * screenshots in both themes, mid-drag among them.
 */
class HudLayoutTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val window = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.screenWidth to it.window.screenHeight }
            context.input.resizeWindow(1280, 720)
            guiScale(context, 2)
            context.enterTheEnd()
            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            context.waitTicks(5)
            SettingsDriver(context).layout()
            context.runOnClient<Nothing> {
                DsModClient.settings.hud.positions.clear()
                DsModClient.saveSettings()
            }
            context.setScreen { null }
            context.restoreDefaultGameOptions()
            context.runOnClient<Nothing> { it.resizeGui() }
            context.input.resizeWindow(window.first, window.second)
        }
    }

    private fun SettingsDriver.layout() {
        val hud = DsModClient.settings.hud
        fun editor(client: net.minecraft.client.Minecraft) = (client.gui.screen() as SettingsScreen).editor("hudLayout") as HudLayoutEditor
        fun point() = context.computeOnClient<Pair<Int, Int>?, Nothing> { editor(it).pointOf(ID) } ?: error("The layout does not show $ID")
        fun placed() = context.computeOnClient<Hud.Placed, Nothing> { Hud.find(ID)!! }

        context.setScreen { SettingsScreen(null, SettingsTabs(at("Interface")), reveal = "hudLayout") }
        context.waitTicks(2)
        spot("hudLayout")
        context.waitTicks(2)
        context.takeScreenshot("hud-layout")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("hud-layout-vanilla")
        theme(context, Theme.CHARCOAL)

        // A drag moves the block, by the map's scale, and the place is kept.
        val start = point()
        move(start.first, start.second)
        context.input.holdMouse(0)
        context.waitTick()
        for (step in 1..4) move(start.first + step * 12, start.second + step * 6)
        context.takeScreenshot("hud-layout-drag")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("hud-layout-drag-vanilla")
        theme(context, Theme.CHARCOAL)
        context.input.releaseMouse(0)
        context.waitTicks(2)
        check(ID in hud.positions) { "A drag on the layout did not place the block" }
        val dropped = placed()

        // Dragged far past the corner, it stops with its far edges on the screen's.
        move(point().first, point().second)
        context.input.holdMouse(0)
        context.waitTick()
        move(point().first + 400, point().second + 400)
        context.input.releaseMouse(0)
        context.waitTicks(2)
        val corner = placed()
        val screen = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.guiScaledWidth to it.window.guiScaledHeight }
        check(corner.x + corner.width * corner.scale <= screen.first + 0.5 && corner.y + corner.height * corner.scale <= screen.second + 0.5) {
            "The block left the screen: ${corner.x}, ${corner.y} on ${screen.first}x${screen.second}"
        }

        // Escape during a drag puts it back where the drag began.
        move(point().first, point().second)
        context.input.holdMouse(0)
        context.waitTick()
        move(point().first - 60, point().second - 30)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        context.input.releaseMouse(0)
        context.waitTicks(2)
        check(placed().x == corner.x && placed().y == corner.y) { "Escape left the block at ${placed().x}, ${placed().y}, not ${corner.x}, ${corner.y}" }
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is SettingsScreen }) { "Escape during a drag closed the settings" }

        // The wheel over the block and + make it bigger, by whole screen pixels.
        val scale = placed().scale
        move(point().first, point().second)
        context.input.scroll(1.0)
        context.waitTicks(2)
        check(placed().scale > scale) { "The wheel did not resize the block: ${placed().scale}" }
        val wheeled = placed().scale
        key(GLFW.GLFW_KEY_EQUAL)
        check(placed().scale > wheeled) { "+ did not resize the block: ${placed().scale}" }
        // Grown in the corner, it moves in rather than past the screen's edge.
        placed().let { check(it.x + it.width * it.scale <= screen.first + 0.5 && it.y + it.height * it.scale <= screen.second + 0.5) { "The grown block left the screen at ${it.x}, ${it.y}" } }

        // The arrows nudge it a pixel, and ten with Shift.
        move(point().first, point().second)
        val x = placed().x
        key(GLFW.GLFW_KEY_LEFT)
        check(placed().x == x - 1) { "Left moved the block to ${placed().x}, not ${x - 1}" }
        key(GLFW.GLFW_KEY_LEFT, shift = true)
        check(placed().x == x - 11) { "Shift + Left moved the block to ${placed().x}, not ${x - 11}" }

        context.takeScreenshot("hud-layout-picked")

        // A right press stacks it again, as in the HUD editor and the chat. An arrow places it once more, and Backspace stacks it.
        click(point().first, point().second, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        check(ID !in hud.positions) { "A right press kept the block's place" }
        key(GLFW.GLFW_KEY_RIGHT)
        check(ID in hud.positions) { "Right did not place the stacked block" }
        key(GLFW.GLFW_KEY_BACKSPACE)
        check(ID !in hud.positions) { "Backspace kept the block's place" }
        key(GLFW.GLFW_KEY_ESCAPE)

        // Move HUD opens the full editor over the game, which returns to the settings.
        val moveHud = spot("moveHud")
        click(moveHud.controlX + 4, moveHud.controlY + 8)
        context.waitForScreen(HudEditorScreen::class.java)
        context.takeScreenshot("hud-layout-full-editor")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is SettingsScreen }) { "The full editor did not return to the settings" }
    }
}
