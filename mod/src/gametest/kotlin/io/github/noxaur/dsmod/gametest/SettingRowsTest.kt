package io.github.noxaur.dsmod.gametest

import com.google.gson.JsonParser
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.feature.general.EquipmentPiece
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
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW
import kotlin.io.path.readText

/**
 * Drives every kind of row of ds-mod's own settings screen against the config, as [rows] says. `OwnSettingsTest`
 * covers the screen around the rows, and `SettingsNarrationTest` what the narrator says of them.
 */
class SettingRowsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        SettingsDriver(context).rows()
        context.setScreen { TitleScreen() }
        restore(context, window)
    }

    /**
     * Drives each kind of row against the config: a press, a drag and the wheel on a number, its field, the keyboard,
     * the changed marker, a choice's menu and steps, a pick's menu of looks, keys, a group's switch, a fold, a span,
     * and a search that finds an option inside a choice. Then a change survives closing and reopening the screen.
     */
    private fun SettingsDriver.rows() {
        val g = { context.computeOnClient<io.github.noxaur.dsmod.core.config.DsModConfig, Nothing> { DsModClient.settings } }
        fun <T> expect(what: String, want: T, read: (io.github.noxaur.dsmod.core.config.DsModConfig) -> T) {
            val got = context.computeOnClient<T, Nothing> { read(DsModClient.settings) }
            check(got == want) { "$what: $got, not $want" }
        }

        // Camera: numbers, a signed one, a choice, keys and two heading switches.
        context.runOnClient<Nothing> { DsModClient.settings.general.cameraTilt = -12f }
        val cameraTabs = SettingsTabs(at("Camera"))
        open(context, cameraTabs)
        val searched = { context.computeOnClient<String, Nothing> { cameraTabs.current.place.query } }
        context.takeScreenshot("own-settings-camera")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-camera-vanilla")
        theme(context, Theme.CHARCOAL)

        var d = spot("cameraDistance")
        // The track runs 70 pixels inside its edge, from 1 to 50.
        click(d.controlX + 1 + 35, d.controlY + 6)
        expect("A press halfway along the track", 25.5f) { it.general.cameraDistance }
        move(d.controlX + 2, d.controlY + 6)
        context.input.holdMouse(0)
        context.waitTick()
        for (x in listOf(20, 50, 90, 140)) move(d.controlX + x, d.controlY + 6)
        context.takeScreenshot("own-settings-number-drag")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        expect("A drag past the track's end", 50f) { it.general.cameraDistance }
        move(d.extraX + 4, d.controlY + 6)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        expect("The wheel down over the field", 49.5f) { it.general.cameraDistance }
        context.input.holdShift()
        context.input.scroll(-1.0)
        context.input.releaseShift()
        context.waitTicks(2)
        expect("The wheel with Shift", 44.5f) { it.general.cameraDistance }
        click(d.extraX + 4, d.controlY + 6)
        context.input.typeChars("12")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-number-field")
        key(GLFW.GLFW_KEY_ENTER)
        expect("Typing 12 and Enter", 12f) { it.general.cameraDistance }
        click(d.extraX + 4, d.controlY + 6)
        context.input.typeChars("x")
        key(GLFW.GLFW_KEY_ENTER)
        expect("Enter on what is not a number keeps the value", 12f) { it.general.cameraDistance }
        // The keyboard: the press above gave the rows the keys, and Right and Left step.
        key(GLFW.GLFW_KEY_RIGHT)
        expect("Right steps", 12.5f) { it.general.cameraDistance }
        key(GLFW.GLFW_KEY_LEFT, shift = true)
        expect("Shift+Left steps by ten", 7.5f) { it.general.cameraDistance }
        context.takeScreenshot("own-settings-changed")
        d = spot("cameraDistance")
        move(d.markerX + 1, d.markerY + 1)
        context.waitTicks(10)
        context.takeScreenshot("own-settings-reset-tip")
        click(d.markerX + 1, d.markerY + 1)
        expect("A press on the changed marker resets", 4f) { it.general.cameraDistance }
        key(GLFW.GLFW_KEY_DOWN)
        key(GLFW.GLFW_KEY_RIGHT)
        expect("Down then Right steps the next row", 0.1f) { it.general.cameraHeight }
        key(GLFW.GLFW_KEY_BACKSPACE)
        expect("Backspace resets", 0f) { it.general.cameraHeight }
        key(GLFW.GLFW_KEY_DOWN)
        context.input.typeChars("-30")
        key(GLFW.GLFW_KEY_ENTER)
        expect("A digit typed on a number opens its field with it", -30f) { it.general.cameraTilt }

        // A choice: a right press steps, the wheel steps, and a press opens its menu.
        val views = spot("cameraViews")
        val viewsAt = { context.computeOnClient<Int, Nothing> { DsModClient.settings.general.cameraViews.ordinal } }
        val first = viewsAt()
        click(views.controlX + 8, views.controlY + 8, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        check(viewsAt() == first + 1) { "A right press on the dropdown did not step it" }
        move(views.controlX + 8, views.controlY + 8)
        context.input.scroll(1.0)
        context.waitTicks(2)
        check(viewsAt() == first) { "The wheel up did not step back" }
        click(views.controlX + 8, views.controlY + 8)
        context.takeScreenshot("own-settings-choice-menu")
        key(GLFW.GLFW_KEY_DOWN)
        key(GLFW.GLFW_KEY_ENTER)
        check(viewsAt() == first + 1) { "Down and Enter in the menu did not pick the next view" }
        // Typing in the open menu lights the next value that starts with the letter, and starts no search.
        click(views.controlX + 8, views.controlY + 8)
        context.input.typeChars("s")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-choice-typed")
        hear(context, "Skip Back, 3 of 3", "S in the menu lit on Skip Front")
        key(GLFW.GLFW_KEY_ENTER)
        check(viewsAt() == 2) { "S and Enter in the menu did not pick Skip Back" }
        check(searched().isEmpty()) { "Typing in the open menu searched for \"${searched()}\"" }

        // Keys: a press records the next key, a right press clears, Backspace resets.
        var k = spot("freeLookKey")
        click(k.controlX + 8, k.controlY + 8)
        context.takeScreenshot("own-settings-keys-recording")
        key(GLFW.GLFW_KEY_K)
        // A real K also types its character after the key, which must not start a search.
        context.input.typeChars("k")
        context.waitTicks(2)
        expect("A press then K", GLFW.GLFW_KEY_K) { it.general.freeLookKey }
        check(searched().isEmpty()) { "The K that set the key also searched for \"${searched()}\"" }
        click(k.controlX + 8, k.controlY + 8, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        expect("A right press clears", -1) { it.general.freeLookKey }
        key(GLFW.GLFW_KEY_BACKSPACE)
        expect("Backspace resets", GLFW.GLFW_KEY_LEFT_ALT) { it.general.freeLookKey }

        // A group's heading switch turns the group off, and its rows dim and stay editable.
        val camera = spot("camera")
        click(camera.controlX + 11, camera.controlY + 6)
        expect("The heading switch", false) { it.general.camera }
        context.takeScreenshot("own-settings-dimmed")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-dimmed-vanilla")
        theme(context, Theme.CHARCOAL)
        d = spot("cameraDistance")
        click(d.controlX + 71, d.controlY + 6)
        expect("A dimmed row stays editable", 50f) { it.general.cameraDistance }
        click(camera.controlX + 11, camera.controlY + 6)
        expect("The heading switch again", true) { it.general.camera }

        // Inventory: the Style pick's menu of looks, and the Custom Look fold.
        open(context, SettingsTabs(at("Inventory")))
        val style = spot("rarityPreset")
        click(style.controlX + 8, style.controlY + 8)
        context.takeScreenshot("own-settings-style-menu")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-style-menu-vanilla")
        theme(context, Theme.CHARCOAL)
        // Showcase is the first tile, and the menu opened lit on Gilded, the current one.
        key(GLFW.GLFW_KEY_LEFT)
        key(GLFW.GLFW_KEY_ENTER)
        expect("Left and Enter in the pick's menu", RarityPreset.SHOWCASE) { it.inventory.rarityPreset }
        // Typing lights a tile by its name, and Escape leaves the style as it was.
        click(style.controlX + 8, style.controlY + 8)
        context.input.typeChars("m")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-pick-typed")
        hear(context, "Minimal, 3 of 4", "M in the pick's menu")
        key(GLFW.GLFW_KEY_ESCAPE)
        expect("Escape from the pick's menu", RarityPreset.SHOWCASE) { it.inventory.rarityPreset }
        val fold = spot("customLook")
        click(fold.x + 20, fold.y + 10)
        val border = spot("border")
        click(border.controlX + 8, border.controlY + 8)
        // The tiles run None, Flat: the menu opened on Rounded, Showcase's own.
        key(GLFW.GLFW_KEY_LEFT)
        key(GLFW.GLFW_KEY_LEFT)
        key(GLFW.GLFW_KEY_ENTER)
        expect("A part of the look", Border.FLAT) { it.inventory.inventoryStyle.border }
        expect("A part switches the style to Custom", RarityPreset.CUSTOM) { it.inventory.rarityPreset }
        val gem = spot("gem")
        click(gem.x + 20, gem.y + gem.height / 2)
        expect("A press anywhere on a switch's row", true) { it.inventory.inventoryStyle.gem }
        context.takeScreenshot("own-settings-custom-look")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-custom-look-vanilla")
        theme(context, Theme.CHARCOAL)
        // A switch's own settings open under it from its button, and close to a summary.
        val lock = spot("slotLock")
        click(lock.extraX + 7, lock.controlY + 6)
        check(context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot("slotLockKey") != null }) { "Slot Lock's button did not open its settings" }
        spot("itemLockKey")
        context.takeScreenshot("own-settings-switch-open")
        val shut = spot("slotLock")
        click(shut.extraX + 7, shut.controlY + 6)
        check(context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot("slotLockKey") == null }) { "Slot Lock's button did not close its settings" }

        // The End: a span under Protector Spawn Beam, drawn by a press and drag on its empty track.
        open(context, SettingsTabs(at("The End")))
        val beacon = spot("protectorSpawnBeam")
        click(beacon.extraX + 7, beacon.controlY + 6)
        val span = spot("beamSections")
        val trackX = span.x + SettingRows.PAD
        val trackY = span.y + span.height - 4 - 8
        val trackWidth = span.width - SettingRows.PAD * 2
        move(trackX + 1 + (trackWidth - 2) * 20 / 200, trackY)
        context.input.holdMouse(0)
        context.waitTick()
        move(trackX + 1 + (trackWidth - 2) * 60 / 200, trackY)
        context.input.releaseMouse(0)
        context.waitTicks(2)
        val sections = context.computeOnClient<Int, Nothing> { DsModClient.settings.end.beamSections.size }
        check(sections == 2) { "A drag on the span's empty track did not draw a second stretch: $sections" }
        context.takeScreenshot("own-settings-span")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-span-vanilla")
        theme(context, Theme.CHARCOAL)

        // A search finds an option inside a choice, and opens the switch that holds it.
        open(context, SettingsTabs(at("General")))
        context.input.typeChars("rngesus")
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot("lootAlertTier") != null }) { "The search for a choice's value shows no Alert Tier" }
        context.takeScreenshot("own-settings-search-option")

        // A link to a row opens the settings there, over the screen shown now, and Escape goes back to it.
        context.setScreen { TitleScreen() }
        context.runOnClient<Nothing> { DsModClient.openSettings("binds") }
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as? SettingsScreen)?.spot("binds") != null }) { "A link to the binds did not open the settings at the list" }
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is TitleScreen }) { "Escape from the settings did not go back to the title screen" }
        context.setScreen { null }

        // An action opens its screen, which returns to the settings.
        open(context, SettingsTabs(at("Contribute")))
        val requests = spot("requests")
        click(requests.controlX + 4, requests.controlY + 8)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen()?.javaClass?.simpleName == "ContributeScreen" }) { "Requests did not open the Contribute screen" }
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is SettingsScreen }) { "The Contribute screen did not return to the settings" }

        // A change is written when the screen closes, and is there when it opens again.
        open(context, SettingsTabs(at("General")))
        val bright = spot("fullbright")
        click(bright.x + 20, bright.y + bright.height / 2)
        expect("A press on a switch's row", true) { it.general.fullbright }
        // The press gave the rows the keys: Space and Enter press the row the keyboard is on.
        key(GLFW.GLFW_KEY_SPACE)
        expect("Space on a switch", false) { it.general.fullbright }
        key(GLFW.GLFW_KEY_ENTER)
        expect("Enter on a switch", true) { it.general.fullbright }
        // The press gave the rows the keys, so the first Escape takes them back and the second closes.
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is TitleScreen }) { "Two Escapes did not close the settings" }
        val saved = JsonParser.parseString(FabricLoader.getInstance().configDir.resolve("dsmod/config.json").readText()).asJsonObject
        check(saved["general"].asJsonObject["fullbright"].asBoolean) { "Fullbright was not written to the config file" }
        open(context, SettingsTabs(at("General")))
        context.takeScreenshot("own-settings-reopened")
        check(g().general.fullbright) { "Fullbright is off after reopening" }

        // The rows put back what they changed, for the tests after this one.
        context.runOnClient<Nothing> {
            for (setting in SettingsTree.sections(DsModClient.settings).flatMap { it.walk() }) setting.reset()
            DsModClient.settings.end.beamSections = DsModConfig().end.beamSections
            // Resetting a part of the custom look switches Style to Custom, so the style is put back after its parts.
            DsModClient.settings.inventory.rarityPreset = DsModConfig().inventory.rarityPreset
            DsModClient.saveSettings()
        }
    }
}
