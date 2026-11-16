package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.edge.ui.SettingRows
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.hear
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import io.github.noxaur.dsmod.gametest.SettingsSteps.theme
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen

/** What the narrator says of ds-mod's own settings screen, as [narration] says. `SettingRowsTest` drives the rows. */
class SettingsNarrationTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        context.input.setCursorPos(0.0, 0.0)
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
        context.runOnClient<Nothing> { DsModClient.settings.general.camera = false }
        open(context, SettingsTabs(at("Camera")))
        over("cameraDistance", "Distance, number, 4.5, changed from 4.0, dimmed, Camera is off")
        context.runOnClient<Nothing> {
            DsModClient.settings.general.camera = true
            DsModClient.settings.general.cameraDistance = 4f
        }

        open(context, SettingsTabs(at("Inventory")))
        over("rarityPreset", "Style, pick, Gilded, 2 of 4")
        // What is open lasts as long as the tabs do, so a test before this one may have opened it.
        if (context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot("border") == null }) {
            val fold = spot("customLook")
            move(fold.x + 20, fold.y + 10)
            context.input.pressMouse(0)
            context.waitTicks(2)
        }
        over("customLook", "Custom Look, fold, expanded")
        over("slotLock", "Slot Lock, switch, on, collapsed")

        open(context, SettingsTabs(at("The End")))
        if (context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot("beamSections") == null }) {
            val beacon = spot("protectorSpawnBeam")
            move(beacon.extraX + 7, beacon.controlY + 6)
            context.input.pressMouse(0)
            context.waitTicks(2)
        }
        over("protectorSpawnBeam", "Protector Spawn Beam, switch, on, expanded")
        over("beamSections", "Beam Timing, span, 175 to 200")

        open(context, SettingsTabs(at("Interface")))
        over("moveHud", "Move HUD, button, Move")
        if (context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot("equipmentPieces", 0) == null }) {
            val list = spot("equipmentPieces")
            move(list.x + 20, list.y + list.height / 2)
            context.input.pressMouse(0)
            context.waitTicks(2)
        }
        over("equipmentPieces", "Pieces, list, Helmet, Chestplate, Leggings, Boots, Pet, 5 of 5 shown, expanded")
        open(context, SettingsTabs(at("General")))
        over("fullbright", "Fullbright, switch, off")
        // The sidebar's lines start 24 down, and The End comes after Favorites, General and its subsections, and the
        // sections between. The bar's first tab is 4 in from the sidebar's 116.
        val tree = SettingsTree.sections(DsModConfig())
        val between = tree.indexOfFirst { it.name == "The End" } - 1
        move(30, 24 + 16 + 16 + tree.first().named.size * 13 + between * 16 + 8)
        hear(context, "The End, section, collapsed", "Over The End in the sidebar")
        move(120 + 30, 9)
        hear(context, "General, tab, selected", "Over the tab")

        // A search paints the words it matched: in names and descriptions, in a fold's name, and in a dropdown's value.
        open(context, SettingsTabs(at("General")))
        context.input.typeChars("gilded look")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-search-marks")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-search-marks-vanilla")
        theme(context, Theme.CHARCOAL)
    }
}
