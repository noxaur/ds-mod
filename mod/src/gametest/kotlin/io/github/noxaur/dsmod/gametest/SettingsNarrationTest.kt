package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.hear
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen

/** What the narrator says of ds-mod's own settings screen, as [narration] says. `SettingRowsTest` drives the rows. */
class SettingsNarrationTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        context.point(0, 0, settle = 0)
        SettingsDriver(context).narration()
        context.setScreen { TitleScreen() }
        restore(context, window)
    }

    /**
     * Hovers a row of each kind and checks what the narrator says of it: its name, kind and value, that it is changed
     * from its default, that a switch dims it, and its new value after a change. The sidebar and the tab bar say
     * what is under the mouse too. Then a search paints the words it matched, in both themes.
     */
    private fun SettingsDriver.narration() {
        fun over(id: String, want: String) {
            val at = spot(id)
            move(at.x + 20, at.y + at.height / 2)
            hear(context, want, "Over $id")
        }

        open(context, SettingsTabs(at("Camera")))
        over("cameraDistance", "Distance, number, 4.0")
        over("cameraTilt", "Tilt, number, 0°")
        over("cameraViews", "F5 Views, choice, All, 1 of 3")
        over("freeLookKey", "Key, key bind, Left Alt")
        over("freeLookMode", "Mode, choice, Hold, 1 of 2")
        over("camera", "Camera, switch, on")
        // A change is said with the row: the wheel over the number's field steps it.
        val distance = spot("cameraDistance")
        move(distance.extraX + 4, distance.controlY + 6)
        context.input.scroll(1.0)
        context.waitTicks(2)
        hear(context, "Distance, number, 4.5, changed from 4.0", "After the wheel")
        context.runOnClient<Nothing> { DsModClient.feature<Camera>().feature.enabled = false }
        open(context, SettingsTabs(at("Camera")))
        over("cameraDistance", "Distance, number, 4.5, changed from 4.0, dimmed, Camera is off")
        context.runOnClient<Nothing> {
            DsModClient.feature<Camera>().feature.enabled = true
            DsModClient.feature<Camera>().distance.value = 4f
        }

        open(context, SettingsTabs(at("Inventory")))
        over("rarityPreset", "Style, pick, Gilded, 2 of 4")
        // What is open lasts as long as the tabs do, so a test before this one may have opened it.
        unfold("border", "customLook") { it.x + 20 to it.y + 10 }
        over("customLook", "Custom Look, fold, expanded")
        over("slot-lock", "Slot Lock, switch, on, collapsed")

        open(context, SettingsTabs(at("The End")))
        unfold("beamSections", "protector-spawn-beam") { it.extraX + 7 to it.controlY + 6 }
        over("protector-spawn-beam", "Protector Spawn Beam, switch, on, expanded")
        over("beamSections", "Beam Timing, span, 175 to 200")

        open(context, SettingsTabs(at("Interface")))
        over("moveHud", "Move HUD, button, Move")
        unfold("equipmentPieces", "equipmentPieces", entry = 0) { it.x + 20 to it.y + it.height / 2 }
        over("equipmentPieces", "Pieces, list, Helmet, Chestplate, Leggings, Boots, Pet, 5 of 5 shown, expanded")
        open(context, SettingsTabs(at("General")))
        over("fullbright", "Fullbright, switch, off")
        // The bar's first tab is 4 in from the sidebar's 116.
        move(30, context.computeOnClient<Int?, Nothing> { (it.gui.screen() as SettingsScreen).line("The End") } ?: error("The sidebar does not show The End"))
        hear(context, "The End, section, collapsed", "Over The End in the sidebar")
        move(120 + 30, 9)
        hear(context, "General, tab, selected", "Over the tab")

        // A search paints the words it matched: in names and descriptions, in a fold's name, and in a dropdown's value.
        open(context, SettingsTabs(at("General")))
        context.input.typeChars("gilded look")
        context.waitTicks(2)
        context.shotInBothThemes("own-settings-search-marks")
    }
}
