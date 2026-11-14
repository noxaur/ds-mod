package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.BeaconScreen
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitRangeSlider
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import kotlin.io.path.readText

/**
 * Opens Boss Beacon's timing screen from its button in the settings and works the slider with the real mouse:
 * a click adds a section, a press and drag draws one, a section drawn or stretched into its neighbor joins it
 * and comes apart again when the mouse backs off, and a right click removes one. Checks each step in the
 * settings, that closing saves them, and that Done goes back to the settings. Read the screenshots in both themes.
 */
class BeaconScreenTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.waitForScreen(TitleScreen::class.java)
        val end = DsModClient.settings.end
        context.runOnClient<Nothing> { DsModClient.openSettings(DsModConfig.End::class.java.getField("editBeam")) }
        context.waitTicks(20)
        val settings = context.computeOnClient<Screen?, Nothing> { it.gui.screen() }
        context.takeScreenshot("beacon-settings")
        // The button in the settings runs this.
        context.runOnClient<Nothing> { end.editBeam.run() }
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
        fun move(value: Int) {
            at(value).let { context.input.setCursorPos(it.first, it.second) }
            context.waitTick()
        }
        fun sections(vararg expected: Section, step: String) =
            check(end.beamSections == expected.toList()) { "$step gave ${end.beamSections}, not ${expected.toList()}" }

        move(60)
        context.input.pressMouse(0)
        context.waitTick()
        sections(Section(48, 73), Section(175, 200), step = "A click at 60")

        move(100)
        context.input.holdMouse(0)
        move(130)
        context.input.releaseMouse(0)
        context.waitTick()
        sections(Section(48, 73), Section(100, 130), Section(175, 200), step = "A drag from 100 to 130")
        context.waitTicks(2)
        context.takeScreenshot("beacon-drawn")

        // Drawn to three short of the last section, the two are one. Backing off parts them, and going in joins them for good.
        move(140)
        context.input.holdMouse(0)
        move(172)
        sections(Section(48, 73), Section(100, 130), Section(140, 200), step = "Drawing from 140 to 172")
        move(160)
        sections(Section(48, 73), Section(100, 130), Section(140, 160), Section(175, 200), step = "Backing off to 160")
        move(185)
        context.input.releaseMouse(0)
        context.waitTick()
        sections(Section(48, 73), Section(100, 130), Section(140, 200), step = "Releasing inside the last section")

        // An end stretched to beside its neighbor joins it too.
        move(129)
        context.input.holdMouse(0)
        move(137)
        context.input.releaseMouse(0)
        context.waitTick()
        sections(Section(48, 73), Section(100, 200), step = "Stretching the end from 130 to 137")
        move(150)
        context.waitTicks(2)
        context.takeScreenshot("beacon-merged")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
        context.waitTicks(2)
        context.takeScreenshot("beacon-vanilla")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

        move(60)
        context.input.pressMouse(1)
        context.waitTick()
        sections(Section(100, 200), step = "A right click on the first section")

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
        val after = context.computeOnClient<Screen?, Nothing> { it.gui.screen() }
        check(after === settings && after != null) { "Done went to $after, not back to the settings $settings" }
        val saved = FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()
        check(""""beamSections":[{"from":100,"to":200}]""" in saved && """"beamInFight":true""" in saved) { "The timing was not saved: $saved" }

        context.runOnClient<Nothing> { end.editBeam.run() }
        context.waitForScreen(BeaconScreen::class.java)
        context.clickScreenButton("Reset")
        check(end.beamSections == listOf(Section(175, 200)) && !end.beamInFight) { "Reset left ${end.beamSections}, fight ${end.beamInFight}" }
        context.clickScreenButton("Done")
        context.setScreen { TitleScreen() }
    }
}
