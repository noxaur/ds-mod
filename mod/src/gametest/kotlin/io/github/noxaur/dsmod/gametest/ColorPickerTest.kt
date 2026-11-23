package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.Color
import io.github.noxaur.dsmod.core.ui.ColorPick
import io.github.noxaur.dsmod.core.ui.Colors
import io.github.noxaur.dsmod.debug.SettingsGalleryScreen
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.KitColorPicker
import io.github.noxaur.dsmod.edge.ui.SettingRows
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

class ColorPickerTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = KitTest.inKit(context) {
        context.setScreen { SettingsGalleryScreen(DsModClient.settings.hud) }
        context.waitTicks(2)
        Picking(context).run()
    }

    private class Picking(context: ClientGameTestContext) : SettingsDriver(context) {
        private fun rows() = context.computeOnClient<SettingRows, Nothing> { (it.gui.screen() as SettingsGalleryScreen).settingRows }

        private fun color() = context.computeOnClient<Int, Nothing> { (rows().lines.first { l -> l.setting.id == "color" }.setting as Color).value }

        private fun picker() = context.computeOnClient<KitColorPicker?, Nothing> { rows().picker() }

        private fun open(): KitColorPicker {
            val row = context.computeOnClient<Spot, Nothing> { rows().spot("color")!! }
            click(row.controlX + 8, row.controlY + 8)
            return picker() ?: error("A press on the color row opened no picker")
        }

        private fun expect(what: String, want: Int, got: Int = color()) = check(got == want) { "$what: ${Colors.show(got)}, not ${Colors.show(want)}" }

        private fun hears(want: String, step: String) {
            val said = context.computeOnClient<String, Nothing> { picker()!!.narrated() }
            check(want in said) { "$step, the narrator says \"$said\", not \"$want\"" }
        }

        fun run() {
            var p = open()
            context.shotInBothThemes("color-picker")
            val right = p.left + KitColorPicker.WIDTH - 1
            click(p.left, p.fieldY)
            expect("A press on the field's top left", 0xFFFFFFFF.toInt())
            click(right, p.fieldY)
            expect("The hue stays while the color is white, so its top right is cyan again", 0xFF00FFFF.toInt())

            hold(right, p.fieldY)
            for (y in listOf(20, 60, 120)) move(right, p.fieldY + y)
            context.takeScreenshot("color-picker-drag")
            expect("A drag past the field's bottom", 0xFF000000.toInt())
            escapeHeld()
            expect("Escape during the drag puts the color back", 0xFF00FFFF.toInt())
            check(picker() != null) { "Escape during the drag closed the picker" }

            click(p.left, p.hueY + 4)
            expect("A press on the hue strip's start", 0xFFFF0000.toInt())
            click(p.left, p.opacityY + 4)
            expect("A press on the opacity strip's start", 0x00FF0000)
            move(p.left + 20, p.opacityY + 4)
            context.input.scroll(1.0)
            context.waitTicks(2)
            check(Colors.opacity(color()) == 1) { "The wheel over the opacity strip made ${Colors.show(color())}" }

            click(p.left + 8, p.rowY + 8)
            context.input.typeChars("f80")
            key(GLFW.GLFW_KEY_ENTER)
            expect("Typing f80 and Enter keeps the opacity", Colors.withOpacity(0xFFFF8800.toInt(), 1))
            click(p.left + 8, p.rowY + 8)
            context.input.typeChars("ff880001")
            key(GLFW.GLFW_KEY_ENTER)
            expect("Typing eight digits sets the opacity too", 0x01FF8800)
            click(p.left + KitColorPicker.HEX_WIDTH + 8, p.rowY + 8)
            key(GLFW.GLFW_KEY_ENTER)
            expect("Enter on the untouched 0% leaves the faint opacity", 0x01FF8800)
            click(p.left + KitColorPicker.HEX_WIDTH + 8, p.rowY + 8)
            context.input.typeChars("50")
            key(GLFW.GLFW_KEY_ENTER)
            expect("Typing 50 into the opacity", Colors.withOpacity(0xFFFF8800.toInt(), 50))
            click(p.left + 8, p.rowY + 8)
            context.input.typeChars("zz")
            context.takeScreenshot("color-picker-bad-hex")
            key(GLFW.GLFW_KEY_ESCAPE)
            expect("Escape in the field leaves the color", Colors.withOpacity(0xFFFF8800.toInt(), 50))
            check(picker() != null) { "Escape in the field closed the picker" }

            val red = 0xFFFF5555.toInt()
            val at = p.swatches.indexOf(red)
            click(p.swatchX(at) + 4, p.swatchY(at) + 4)
            expect("A press on a swatch", red)
            key(GLFW.GLFW_KEY_RIGHT)
            hears("#FF55FF, swatch", "Right on the swatches")
            key(GLFW.GLFW_KEY_ENTER)
            expect("Enter on the next swatch", p.swatches[at + 1])
            key(GLFW.GLFW_KEY_TAB)
            hears("Saturation", "Tab from the swatches goes round to the field")
            val before = ColorPick(color())
            key(GLFW.GLFW_KEY_LEFT, shift = true)
            check(ColorPick(color()).saturation < before.saturation - 0.05f) { "Shift+Left on the field did not take ten percent of saturation" }
            key(GLFW.GLFW_KEY_TAB)
            key(GLFW.GLFW_KEY_RIGHT)
            hears("Hue 301 degrees", "Right on the hue strip")
            val last = color()
            key(GLFW.GLFW_KEY_ESCAPE)
            check(picker() == null) { "Escape did not close the picker" }

            p = open()
            check(p.swatches.first() == last) { "The picker does not offer the recent color first" }
            context.takeScreenshot("color-picker-recent")
            val row = context.computeOnClient<Spot, Nothing> { rows().spot("color")!! }
            click(row.x + 4, row.y + 4)
            check(picker() == null) { "A press outside did not close the picker" }

            context.input.typeChars("#")
            context.waitTicks(2)
            check(picker() != null) { "# on the color row opened no picker" }
            context.input.typeChars("00ff00")
            key(GLFW.GLFW_KEY_ENTER)
            check(Colors.rgb(color()) == 0x00FF00) { "#00ff00 typed on the row made ${Colors.show(color())}" }
            key(GLFW.GLFW_KEY_ESCAPE)
        }
    }
}
