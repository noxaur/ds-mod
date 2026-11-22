package io.github.noxaur.dsmod.gametest

import com.google.gson.JsonParser
import io.github.noxaur.dsmod.core.feature.end.ProtectorSpawnBeam
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.TitleScreen
import kotlin.io.path.readText

/**
 * Works Protector Spawn Beam's Beam Timing row with the real mouse: a click adds a section, a press and drag draws one, a
 * section drawn or stretched into its neighbor joins it and comes apart again when the mouse backs off, and a right
 * click removes one. Checks each step in the settings, that During the Fight turns on from its row, that closing
 * saves both, and that the changed marker puts them back. Read the screenshots in both themes.
 */
class BeamTimingTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        val driver = SettingsDriver(context)
        val protectorSpawnBeam = DsModClient.feature<ProtectorSpawnBeam>()
        try {
            open(context, SettingsTabs(at("The End")))
            // Protector Spawn Beam's button opens its settings, unless an earlier test left them open.
            val beacon = driver.spot("protector-spawn-beam")
            if (context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot("beamSections") == null }) driver.click(beacon.extraX + 7, beacon.controlY + 6)
            check(protectorSpawnBeam.sections.value == listOf(Section(175, 200))) { "The settings start with ${protectorSpawnBeam.sections.value}" }
            val span = driver.spot("beamSections")
            context.takeScreenshot("beam-timing-default")

            // Window pixels of a value on the track, at the middle of the track's height.
            fun move(value: Int) = context.point(span.sliderX + 1 + value * (span.sliderWidth - 2) / 200.0, span.sliderY + 8.0)
            fun sections(vararg expected: Section, step: String) =
                check(protectorSpawnBeam.sections.value == expected.toList()) { "$step gave ${protectorSpawnBeam.sections.value}, not ${expected.toList()}" }

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
            context.takeScreenshot("beam-timing-drawn")

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
            context.takeScreenshot("beam-timing-merged")
            context.inTheme(Theme.VANILLA) { context.takeScreenshot("beam-timing-vanilla") }

            move(60)
            context.input.pressMouse(1)
            context.waitTick()
            sections(Section(100, 200), step = "A right click on the first section")

            val fight = driver.spot("beamInFight")
            driver.click(fight.x + 20, fight.y + fight.height / 2)
            check(protectorSpawnBeam.inFight.value) { "A press on During the Fight did not turn it on" }

            context.setScreen { TitleScreen() }
            val saved = JsonParser.parseString(FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()).asJsonObject["end"].asJsonObject
            check(saved["beamSections"].toString() == """[{"from":100,"to":200}]""" && saved["beamInFight"].asBoolean) { "The timing was not saved: $saved" }

            // The changed marker puts each back to its default.
            open(context, SettingsTabs(at("The End")))
            for (id in listOf("beamSections", "beamInFight")) driver.spot(id).let { driver.click(it.markerX + 2, it.markerY) }
            check(protectorSpawnBeam.sections.value == protectorSpawnBeam.sections.default && !protectorSpawnBeam.inFight.value) { "The markers left ${protectorSpawnBeam.sections.value}, fight ${protectorSpawnBeam.inFight.value}" }
        } finally {
            context.runOnClient<Nothing> {
                protectorSpawnBeam.sections.reset()
                protectorSpawnBeam.inFight.value = false
                DsModClient.saveSettings()
            }
            context.setScreen { TitleScreen() }
            restore(context, window)
        }
    }
}
