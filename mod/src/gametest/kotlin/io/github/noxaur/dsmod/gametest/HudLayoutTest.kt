package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.HudEditorScreen
import io.github.noxaur.dsmod.edge.HudLayoutEditor
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

class HudLayoutTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val window = SettingsSteps.windowSize(context)
            SettingsSteps.fit(context, 2)
            context.showDragonTimer(world)
            context.waitTicks(5)
            SettingsDriver(context).layout()
            context.runOnClient<Nothing> {
                DsModClient.settings.hud.positions.value = emptyMap()
                DsModClient.saveSettings()
            }
            context.setScreen { null }
            SettingsSteps.restore(context, window)
        }
    }

    private fun SettingsDriver.layout() {
        val hud = DsModClient.settings.hud
        fun editor(client: net.minecraft.client.Minecraft) = (client.gui.screen() as SettingsScreen).editor("hudLayout") as HudLayoutEditor
        fun point() = context.computeOnClient<Pair<Int, Int>?, Nothing> { editor(it).pointOf(DRAGON_TIMER) } ?: error("The layout does not show $DRAGON_TIMER")
        fun placed() = context.computeOnClient<Hud.Placed, Nothing> { Hud.find(DRAGON_TIMER)!! }

        context.setScreen { DsModClient.settingsScreen(null, SettingsTabs(at("Interface")), reveal = "hudLayout") }
        context.waitTicks(2)
        spot("hudLayout")
        context.waitTicks(2)
        context.shotInBothThemes("hud-layout")

        val start = point()
        hold(start.first, start.second)
        for (step in 1..4) move(start.first + step * 12, start.second + step * 6)
        context.shotInBothThemes("hud-layout-drag")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        check(DRAGON_TIMER in hud.positions.value) { "A drag on the layout did not place the block" }
        val dropped = placed()

        hold(point().first, point().second)
        move(point().first + 400, point().second + 400)
        context.input.releaseMouse(0)
        context.waitTicks(2)
        val corner = placed()
        val screen = context.guiSize()
        check(corner.x + corner.width * corner.scale <= screen.first + 0.5 && corner.y + corner.height * corner.scale <= screen.second + 0.5) {
            "The block left the screen: ${corner.x}, ${corner.y} on ${screen.first}x${screen.second}"
        }

        hold(point().first, point().second)
        move(point().first - 60, point().second - 30)
        escapeHeld()
        check(placed().x == corner.x && placed().y == corner.y) { "Escape left the block at ${placed().x}, ${placed().y}, not ${corner.x}, ${corner.y}" }
        keptOpen()

        val scale = placed().scale
        move(point().first, point().second)
        context.input.scroll(1.0)
        context.waitTicks(2)
        check(placed().scale > scale) { "The wheel did not resize the block: ${placed().scale}" }
        val wheeled = placed().scale
        key(GLFW.GLFW_KEY_EQUAL)
        check(placed().scale > wheeled) { "+ did not resize the block: ${placed().scale}" }
        placed().let { check(it.x + it.width * it.scale <= screen.first + 0.5 && it.y + it.height * it.scale <= screen.second + 0.5) { "The grown block left the screen at ${it.x}, ${it.y}" } }

        move(point().first, point().second)
        val x = placed().x
        key(GLFW.GLFW_KEY_LEFT)
        check(placed().x == x - 1) { "Left moved the block to ${placed().x}, not ${x - 1}" }
        key(GLFW.GLFW_KEY_LEFT, shift = true)
        check(placed().x == x - 11) { "Shift + Left moved the block to ${placed().x}, not ${x - 11}" }

        context.takeScreenshot("hud-layout-picked")

        click(point().first, point().second, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        check(DRAGON_TIMER !in hud.positions.value) { "A right press kept the block's place" }
        key(GLFW.GLFW_KEY_RIGHT)
        check(DRAGON_TIMER in hud.positions.value) { "Right did not place the stacked block" }
        key(GLFW.GLFW_KEY_BACKSPACE)
        check(DRAGON_TIMER !in hud.positions.value) { "Backspace kept the block's place" }
        key(GLFW.GLFW_KEY_ESCAPE)

        val moveHud = spot("moveHud")
        click(moveHud.controlX + 4, moveHud.controlY + 8)
        context.waitForScreen(HudEditorScreen::class.java)
        context.takeScreenshot("hud-layout-full-editor")
        key(GLFW.GLFW_KEY_ESCAPE)
        context.checkClient("The full editor did not return to the settings") { it.gui.screen() is SettingsScreen }
    }
}
