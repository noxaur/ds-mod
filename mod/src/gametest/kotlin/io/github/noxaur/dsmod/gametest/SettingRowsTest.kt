package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.container.MarkStyle
import io.github.noxaur.dsmod.core.feature.end.ProtectorSpawnBeam
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.FreeLook
import io.github.noxaur.dsmod.core.feature.general.Fullbright
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.edge.ui.SettingRows
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import io.github.noxaur.dsmod.gametest.SettingsSteps.hear
import io.github.noxaur.dsmod.gametest.SettingsSteps.open
import io.github.noxaur.dsmod.gametest.SettingsSteps.restore
import io.github.noxaur.dsmod.gametest.SettingsSteps.savedConfig
import io.github.noxaur.dsmod.gametest.SettingsSteps.setUp
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

/** @keep Covers [io.github.noxaur.dsmod.edge.Keys], [io.github.noxaur.dsmod.edge.mixin.PerspectiveKeyMixin]. */
class SettingRowsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        SettingsDriver(context).rows()
        context.setScreen { TitleScreen() }
        restore(context, window)
    }

    private fun SettingsDriver.rows() {
        fun <T> expect(what: String, want: T, read: () -> T) {
            val got = context.computeOnClient<T, Nothing> { read() }
            check(got == want) { "$what: $got, not $want" }
        }

        context.runOnClient<Nothing> { DsModClient.feature<Camera>().tilt.value = -12f }
        val cameraTabs = SettingsTabs(at("Camera"))
        open(context, cameraTabs)
        val searched = { context.computeOnClient<String, Nothing> { cameraTabs.current.place.query } }
        context.shotInBothThemes("own-settings-camera")

        var d = spot("cameraDistance")
        click(d.controlX + 1 + 35, d.controlY + 6)
        expect("A press halfway along the track", 25.5f) { DsModClient.feature<Camera>().distance.value }
        move(d.controlX + 2, d.controlY + 6)
        context.input.holdMouse(0)
        context.waitTick()
        for (x in listOf(20, 50, 90, 140)) move(d.controlX + x, d.controlY + 6)
        context.takeScreenshot("own-settings-number-drag")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        expect("A drag past the track's end", 50f) { DsModClient.feature<Camera>().distance.value }
        move(d.extraX + 4, d.controlY + 6)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        expect("The wheel down over the field", 49.5f) { DsModClient.feature<Camera>().distance.value }
        context.input.holdShift()
        context.input.scroll(-1.0)
        context.input.releaseShift()
        context.waitTicks(2)
        expect("The wheel with Shift", 44.5f) { DsModClient.feature<Camera>().distance.value }
        click(d.extraX + 4, d.controlY + 6)
        context.input.typeChars("12")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-number-field")
        key(GLFW.GLFW_KEY_ENTER)
        expect("Typing 12 and Enter", 12f) { DsModClient.feature<Camera>().distance.value }
        click(d.extraX + 4, d.controlY + 6)
        context.input.typeChars("x")
        key(GLFW.GLFW_KEY_ENTER)
        expect("Enter on what is not a number keeps the value", 12f) { DsModClient.feature<Camera>().distance.value }
        key(GLFW.GLFW_KEY_RIGHT)
        expect("Right steps", 12.5f) { DsModClient.feature<Camera>().distance.value }
        key(GLFW.GLFW_KEY_LEFT, shift = true)
        expect("Shift+Left steps by ten", 7.5f) { DsModClient.feature<Camera>().distance.value }
        context.takeScreenshot("own-settings-changed")
        d = spot("cameraDistance")
        move(d.markerX + 1, d.markerY + 1)
        context.waitTicks(10)
        context.takeScreenshot("own-settings-reset-tip")
        click(d.markerX + 1, d.markerY + 1)
        expect("A press on the changed marker resets", 4f) { DsModClient.feature<Camera>().distance.value }
        key(GLFW.GLFW_KEY_DOWN)
        key(GLFW.GLFW_KEY_RIGHT)
        expect("Down then Right steps the next row", 0.1f) { DsModClient.feature<Camera>().height.value }
        key(GLFW.GLFW_KEY_BACKSPACE)
        expect("Backspace resets", 0f) { DsModClient.feature<Camera>().height.value }
        key(GLFW.GLFW_KEY_DOWN)
        context.input.typeChars("-30")
        key(GLFW.GLFW_KEY_ENTER)
        expect("A digit typed on a number opens its field with it", -30f) { DsModClient.feature<Camera>().tilt.value }

        val views = spot("cameraViews")
        val viewsAt = { context.computeOnClient<Int, Nothing> { DsModClient.feature<Camera>().views.value.ordinal } }
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
        click(views.controlX + 8, views.controlY + 8)
        context.input.typeChars("s")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-choice-typed")
        hear(context, "Skip Back, 3 of 3", "S in the menu lit on Skip Front")
        key(GLFW.GLFW_KEY_ENTER)
        check(viewsAt() == 2) { "S and Enter in the menu did not pick Skip Back" }
        check(searched().isEmpty()) { "Typing in the open menu searched for \"${searched()}\"" }

        var k = spot("freeLookKey")
        click(k.controlX + 8, k.controlY + 8)
        context.takeScreenshot("own-settings-keys-recording")
        key(GLFW.GLFW_KEY_K)
        context.input.typeChars("k")
        context.waitTicks(2)
        expect("A press then K", GLFW.GLFW_KEY_K) { DsModClient.feature<FreeLook>().key.value }
        check(searched().isEmpty()) { "The K that set the key also searched for \"${searched()}\"" }
        val left = GLFW.GLFW_MOUSE_BUTTON_LEFT
        val right = GLFW.GLFW_MOUSE_BUTTON_RIGHT
        for ((button, onKey, bound) in listOf(Triple(left, true, left), Triple(right, true, right), Triple(left, false, right))) {
            click(k.controlX + 8, k.controlY + 8)
            val (x, y) = if (onKey) k.controlX + 8 to k.controlY + 8 else k.x + 4 to k.y + 4
            click(x, y, button)
            expect("Mouse button $button ${if (onKey) "on" else "off"} the listening key", bound) { DsModClient.feature<FreeLook>().key.value }
        }
        click(k.controlX + 8, k.controlY + 8, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        expect("A right press clears", -1) { DsModClient.feature<FreeLook>().key.value }
        key(GLFW.GLFW_KEY_BACKSPACE)
        expect("Backspace resets", GLFW.GLFW_KEY_LEFT_ALT) { DsModClient.feature<FreeLook>().key.value }

        val camera = spot("camera")
        click(camera.controlX + 11, camera.controlY + 6)
        expect("The heading switch", false) { DsModClient.feature<Camera>().feature.enabled }
        context.shotInBothThemes("own-settings-dimmed")
        d = spot("cameraDistance")
        click(d.controlX + 71, d.controlY + 6)
        expect("A dimmed row stays editable", 50f) { DsModClient.feature<Camera>().distance.value }
        click(camera.controlX + 11, camera.controlY + 6)
        expect("The heading switch again", true) { DsModClient.feature<Camera>().feature.enabled }

        open(context, SettingsTabs(at("Inventory")))
        val style = spot("style")
        click(style.controlX + 8, style.controlY + 8)
        context.shotInBothThemes("own-settings-style-menu")
        key(GLFW.GLFW_KEY_RIGHT)
        key(GLFW.GLFW_KEY_ENTER)
        expect("Right and Enter in the pick's menu", MarkStyle.AURA) { DsModClient.feature<ItemRarity>().inventoryStyle.value.mark }
        click(style.controlX + 8, style.controlY + 8)
        context.input.typeChars("u")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-pick-typed")
        hear(context, "Underglow, 3 of 3", "U in the pick's menu")
        key(GLFW.GLFW_KEY_ESCAPE)
        expect("Escape from the pick's menu", MarkStyle.AURA) { DsModClient.feature<ItemRarity>().inventoryStyle.value.mark }
        val sparkles = spot("sparkles")
        click(sparkles.x + 20, sparkles.y + sparkles.height / 2)
        expect("A press anywhere on a switch's row", true) { DsModClient.feature<ItemRarity>().inventoryStyle.value.sparkles }
        expect("A row of the look keeps the rest of it", MarkStyle.AURA) { DsModClient.feature<ItemRarity>().inventoryStyle.value.mark }
        context.shotInBothThemes("own-settings-rarity-look")
        toggle("slot-lock")
        check(shows("slotLockKey")) { "Slot Lock's button did not open its settings" }
        val slotKey = spot("slotLockKey")
        click(slotKey.controlX + 8, slotKey.controlY + 8)
        click(slotKey.controlX + 8, slotKey.controlY + 8)
        expect("A left click on the listening Slot Key", GLFW.GLFW_KEY_L) { DsModClient.feature<SlotLock>().slotLockKey.value }
        spot("itemLockKey")
        context.takeScreenshot("own-settings-switch-open")
        toggle("slot-lock")
        check(!shows("slotLockKey")) { "Slot Lock's button did not close its settings" }

        open(context, SettingsTabs(at("The End")))
        toggle("protector-spawn-beam")
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
        val sections = context.computeOnClient<Int, Nothing> { DsModClient.feature<ProtectorSpawnBeam>().sections.value.size }
        check(sections == 2) { "A drag on the span's empty track did not draw a second stretch: $sections" }
        context.shotInBothThemes("own-settings-span")

        open(context, SettingsTabs(at("General")))
        context.input.typeChars("rngesus")
        context.waitTicks(2)
        check(shows("lootAlertTier")) { "The search for a choice's value shows no Alert Tier" }
        context.takeScreenshot("own-settings-search-option")

        context.setScreen { TitleScreen() }
        context.runOnClient<Nothing> { DsModClient.openSettings("binds") }
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as? SettingsScreen)?.spot("binds") != null }) { "A link to the binds did not open the settings at the list" }
        key(GLFW.GLFW_KEY_ESCAPE)
        context.checkClient("Escape from the settings did not go back to the title screen") { it.gui.screen() is TitleScreen }
        context.setScreen { null }

        open(context, SettingsTabs(at("Contribute")))
        val requests = spot("requests")
        click(requests.controlX + 4, requests.controlY + 8)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen()?.javaClass?.simpleName == "ContributeScreen" }) { "Requests did not open the Contribute screen" }
        key(GLFW.GLFW_KEY_ESCAPE)
        context.checkClient("The Contribute screen did not return to the settings") { it.gui.screen() is SettingsScreen }

        open(context, SettingsTabs(at("General")))
        val bright = spot("fullbright")
        click(bright.x + 20, bright.y + bright.height / 2)
        expect("A press on a switch's row", true) { DsModClient.feature<Fullbright>().feature.enabled }
        key(GLFW.GLFW_KEY_SPACE)
        expect("Space on a switch", false) { DsModClient.feature<Fullbright>().feature.enabled }
        key(GLFW.GLFW_KEY_ENTER)
        expect("Enter on a switch", true) { DsModClient.feature<Fullbright>().feature.enabled }
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        context.checkClient("Two Escapes did not close the settings") { it.gui.screen() is TitleScreen }
        val saved = savedConfig()
        check(saved["general"].asJsonObject["fullbright"].asBoolean) { "Fullbright was not written to the config file" }
        open(context, SettingsTabs(at("General")))
        context.takeScreenshot("own-settings-reopened")
        check(DsModClient.feature<Fullbright>().feature.enabled) { "Fullbright is off after reopening" }

        context.runOnClient<Nothing> {
            for (setting in SettingsTree(DsModClient.settings).sections.flatMap { it.walk() }) setting.reset()
            DsModClient.feature<ProtectorSpawnBeam>().sections.reset()
            DsModClient.saveSettings()
        }
    }
}
