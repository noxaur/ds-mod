package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.BeaconScreen
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitRangeSlider
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.TitleScreen
import kotlin.io.path.readText

/**
 * Opens Boss Beacon's timing screen and works the slider with the real mouse: adds a section, stretches it,
 * slides it against its neighbor, removes it, and turns the fight on. Checks each step in the settings and that
 * closing saves them. Read the screenshots in both themes.
 */
class BeaconScreenTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.waitForScreen(TitleScreen::class.java)
        val end = DsModClient.settings.end
        context.runOnClient<Nothing> { DsModClient.openBeaconEditor() }
        context.waitForScreen(BeaconScreen::class.java)
        context.waitTicks(2)
        context.takeScreenshot("beacon-default")
        check(end.beamSections == listOf(Section(175, 200))) { "The settings start with ${end.beamSections}" }

        // Window pixels of a value on the track, and of the middle of the track's height.
        fun at(value: Int) = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
            val slider = client.gui.screen()!!.children().filterIsInstance<KitRangeSlider>().single()
            val scale = client.window.guiScale
            (slider.x + 1 + value * (slider.width - 2) / 200.0) * scale to (slider.y + 8.0) * scale
        }
        fun move(value: Int) = at(value).let { context.input.setCursorPos(it.first, it.second) }
        fun drag(from: Int, to: Int) {
            move(from)
            context.input.holdMouse(0)
            context.waitTick()
            move(to)
            context.waitTick()
            context.input.releaseMouse(0)
            context.waitTick()
        }

        move(60)
        context.input.pressMouse(0)
        context.waitTick()
        check(end.beamSections == listOf(Section(48, 73), Section(175, 200))) { "A click at 60 gave ${end.beamSections}" }

        drag(72, 120)
        check(end.beamSections.first() == Section(48, 120)) { "Dragging the end to 120 gave ${end.beamSections}" }
        drag(84, 190)
        check(end.beamSections.first() == Section(98, 170)) { "Sliding toward the next section gave ${end.beamSections}, not one that stops 5 short of it" }
        move(130)
        context.waitTicks(2)
        context.takeScreenshot("beacon-two-sections")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
        context.waitTicks(2)
        context.takeScreenshot("beacon-vanilla")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

        move(185)
        context.input.pressMouse(1)
        context.waitTick()
        check(end.beamSections == listOf(Section(98, 170))) { "A right click on the last section left ${end.beamSections}" }

        val box = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
            val box = client.gui.screen()!!.children().filterIsInstance<KitCheckbox>().single()
            (box.x + 5.0) * client.window.guiScale to (box.y + 5.0) * client.window.guiScale
        }
        context.input.setCursorPos(box.first, box.second)
        context.input.pressMouse(0)
        context.waitTick()
        check(end.beamInFight) { "The checkbox did not turn the fight on" }

        context.clickScreenButton("Done")
        context.waitTicks(20)
        val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
        check(""""beamSections":[{"from":98,"to":170}]""" in saved && """"beamInFight":true""" in saved) { "The timing was not saved: $saved" }

        context.runOnClient<Nothing> { DsModClient.openBeaconEditor() }
        context.waitForScreen(BeaconScreen::class.java)
        context.clickScreenButton("Reset")
        check(end.beamSections == listOf(Section(175, 200)) && !end.beamInFight) { "Reset left ${end.beamSections}, fight ${end.beamInFight}" }
        context.clickScreenButton("Done")
        context.setScreen { TitleScreen() }
    }
}
