package io.github.noxaur.dsmod.gametest

import com.google.gson.JsonParser
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.ui.Place
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.edge.ui.SettingRows
import net.fabricmc.loader.api.FabricLoader
import kotlin.io.path.readText
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

/**
 * Screenshots ds-mod's own settings screen in both themes: a section with its subsections, a row's tooltip, every
 * section expanded, a search with hits and one with none, the narrow sidebar at GUI scale 3, and the tab bar with
 * several tabs, a blank one, a drag, an overflow and the 427x240 size. Each tab press, drag and key is checked
 * against the tabs, and the tabs outlive the screen. Escape goes back to the screen it was opened over. Then every
 * kind of row is driven against the config, as [rows] says, and each is narrated, as [narration] says.
 */
class OwnSettingsTest : FabricClientGameTest {
    private val names = SettingsTree.sections(DsModConfig()).map { it.name }

    override fun runTest(context: ClientGameTestContext) {
        context.waitForScreen(TitleScreen::class.java)
        val window = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.screenWidth to it.window.screenHeight }
        context.input.resizeWindow(1280, 720)
        guiScale(context, 2)
        open(context, SettingsTabs(Place(0)))
        context.takeScreenshot("own-settings-charcoal")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-vanilla")
        theme(context, Theme.CHARCOAL)

        // GUI pixels, at scale 2. The sidebar's list starts 24 down, with sections 16 tall and subsections 13.
        fun move(x: Int, y: Int) {
            context.input.setCursorPos(x * 2.0, y * 2.0)
            context.waitTick()
        }
        fun click(x: Int, y: Int, button: Int = 0) {
            move(x, y)
            context.input.pressMouse(button)
            context.waitTicks(2)
        }
        fun key(key: Int, control: Boolean = false, shift: Boolean = false, alt: Boolean = false) {
            if (control) context.input.holdControl()
            if (shift) context.input.holdShift()
            if (alt) context.input.holdAlt()
            context.input.pressKey(key)
            if (alt) context.input.releaseAlt()
            if (shift) context.input.releaseShift()
            if (control) context.input.releaseControl()
            context.waitTicks(2)
        }

        // Inventory is under The End's three subsections and Combat. Its first row, under the mouse, lifts.
        click(30, 24 + 16 + 3 * 13 + 16 + 8)
        move(160, 18 + 8 + 2 + 12 + 4 + 10)
        context.waitTicks(10)
        context.takeScreenshot("own-settings-hover")

        // The button beside Done collapses every section while one is open, and then expands them all.
        click(116 - 1 - 4 - 8, 360 - 4 - 8)
        context.takeScreenshot("own-settings-collapsed")
        click(116 - 1 - 4 - 8, 360 - 4 - 8)
        context.takeScreenshot("own-settings-expanded")
        click(116 - 1 - 4 - 8, 360 - 4 - 8)

        // From the top: Down to Combat, Right opens it, Down onto Loot, and Enter jumps there.
        open(context, SettingsTabs(Place(0)))
        for (k in listOf(GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_ENTER)) {
            context.input.pressKey(k)
            context.waitTick()
        }
        context.waitTicks(10)
        context.takeScreenshot("own-settings-keys")
        // The wheel over the pane scrolls it.
        move(400, 200)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-wheel")
        // Ctrl+F takes the keyboard to the search field.
        key(GLFW.GLFW_KEY_F, control = true)
        context.input.typeChars("eyes")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-search")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-search-vanilla")
        theme(context, Theme.CHARCOAL)
        context.input.typeChars("zz")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-empty")

        // Tabs. With The End open, its rows are The End 24, Dragon 40, Protector 53, Drops 66, then Combat 79.
        val tabs = SettingsTabs(Place(0))
        open(context, tabs)
        context.input.holdControl()
        click(30, 79 + 8)
        context.input.releaseControl()
        expect(context, tabs, "[The End] Combat", "A Ctrl press on a section opens it after the selected tab, in the background")
        click(40, 53 + 6, GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        expect(context, tabs, "[The End] The End Combat", "A middle press on a subsection opens its section")
        // The bar's tabs start 4 in from the sidebar's 116, each 92 wide and 1 apart, and the plus comes after them.
        click(120 + 3 * 93 + 7, 9)
        expect(context, tabs, "The End [New Tab] The End Combat", "The plus opens a blank tab after the selected one")
        context.input.typeChars("eyes")
        context.waitTicks(2)
        expect(context, tabs, "The End [eyes] The End Combat", "Typing turns the new tab into a search")
        key(GLFW.GLFW_KEY_T, control = true)
        expect(context, tabs, "The End eyes [New Tab] The End Combat", "Ctrl+T opens a blank tab")
        context.takeScreenshot("own-settings-tab-blank")
        // Five tabs share the room, so each is 85 wide.
        click(120 + 3 * 86 + 30, 9)
        expect(context, tabs, "The End eyes New Tab [The End] Combat", "A press selects a tab")
        val marked = context.computeOnClient<Int, Nothing> { tabs.current.current }
        check(marked == 1) { "The tab opened from Protector marks subsection $marked, not Protector" }
        move(120 + 1 * 86 + 30, 9)
        context.takeScreenshot("own-settings-tabs")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-tabs-vanilla")
        theme(context, Theme.CHARCOAL)

        // A drag past a neighbor's middle moves the tab there, and the tab follows the mouse.
        move(120 + 4 * 86 + 20, 9)
        context.input.holdMouse(0)
        context.waitTick()
        for (x in listOf(440, 400, 360, 330, 324)) move(x, 9)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-tab-drag")
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "The dragged tab is selected and passed two neighbors")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "Releasing keeps the tab where it was dropped")

        key(GLFW.GLFW_KEY_W, control = true)
        expect(context, tabs, "The End eyes [New Tab] The End", "Ctrl+W closes the tab and selects the one that took its place")
        key(GLFW.GLFW_KEY_T, control = true, shift = true)
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "Ctrl+Shift+T reopens it where it was")
        key(GLFW.GLFW_KEY_TAB, control = true)
        expect(context, tabs, "The End eyes Combat [New Tab] The End", "Ctrl+Tab selects the next tab")
        key(GLFW.GLFW_KEY_TAB, control = true, shift = true)
        expect(context, tabs, "The End eyes [Combat] New Tab The End", "Ctrl+Shift+Tab selects the one before")
        key(GLFW.GLFW_KEY_9, control = true)
        expect(context, tabs, "The End eyes Combat New Tab [The End]", "Ctrl+9 selects the last tab")
        key(GLFW.GLFW_KEY_1, control = true)
        expect(context, tabs, "[The End] eyes Combat New Tab The End", "Ctrl+1 selects the first tab")

        // A press in the sidebar takes the tab there, and the history walks back and forward.
        click(30, 79 + 8)
        expect(context, tabs, "[Combat] eyes Combat New Tab The End", "A press on a section goes there in the same tab")
        key(GLFW.GLFW_KEY_LEFT, alt = true)
        expect(context, tabs, "[The End] eyes Combat New Tab The End", "Alt+Left goes back")
        key(GLFW.GLFW_KEY_RIGHT, alt = true)
        expect(context, tabs, "[Combat] eyes Combat New Tab The End", "Alt+Right goes forward")
        click(400, 200, GLFW.GLFW_MOUSE_BUTTON_4)
        expect(context, tabs, "[The End] eyes Combat New Tab The End", "The mouse's back button goes back")
        click(400, 200, GLFW.GLFW_MOUSE_BUTTON_5)
        expect(context, tabs, "[Combat] eyes Combat New Tab The End", "The mouse's forward button goes forward")

        // The × shows on the tab under the mouse, 5 in from its right, and a middle press closes a tab too.
        click(120 + 86 + 85 - 5 - 4, 9)
        expect(context, tabs, "[Combat] Combat New Tab The End", "A press on the × closes that tab")
        click(120 + 86 + 30, 9, GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
        expect(context, tabs, "[Combat] New Tab The End", "A middle press closes a tab")

        // Too many tabs: they shrink to 44 and the strip scrolls, keeping the selected one in sight.
        repeat(8) { key(GLFW.GLFW_KEY_T, control = true) }
        expect(context, tabs, "Combat New Tab New Tab New Tab New Tab New Tab New Tab New Tab [New Tab] New Tab The End", "Ctrl+T opens each after the last one opened")
        context.input.typeChars("loot")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-tab-overflow")
        // The wheel down scrolls the strip on to the tabs past its right end.
        move(300, 9)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-tab-overflow-wheel")

        // The tabs outlive the screen, as a browser's session does.
        val before = context.computeOnClient<Int, Nothing> { SettingsScreen.session.size }
        context.setScreen { SettingsScreen(TitleScreen()) }
        context.waitTicks(2)
        key(GLFW.GLFW_KEY_T, control = true)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        context.setScreen { SettingsScreen(TitleScreen()) }
        context.waitTicks(2)
        val after = context.computeOnClient<Int, Nothing> { SettingsScreen.session.size }
        check(after == before + 1) { "The settings opened again with $after tabs, not the ${before + 1} they closed with" }

        rows(context.also { context.input.setCursorPos(0.0, 0.0) })
        narration(context.also { context.input.setCursorPos(0.0, 0.0) })

        // The smallest window the game allows at GUI scale 2.
        context.input.resizeWindow(854, 480)
        open(context, SettingsTabs(Place(5)))
        context.takeScreenshot("own-settings-small-camera")
        val small = SettingsTabs(Place(0))
        context.runOnClient<Nothing> {
            small.open(Place(4))
            small.open(Place(2, "slot"))
            small.open(Place.BLANK)
            small.select(2)
        }
        open(context, small)
        context.takeScreenshot("own-settings-small")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-small-vanilla")
        theme(context, Theme.CHARCOAL)
        context.input.resizeWindow(1280, 720)

        guiScale(context, 3)
        open(context, SettingsTabs(Place(0)))
        context.input.pressKey(GLFW.GLFW_KEY_END)
        context.waitTicks(2)
        context.takeScreenshot("own-settings-gui3-end")

        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        val back = context.computeOnClient<Any?, Nothing> { it.gui.screen() }
        check(back is TitleScreen) { "Escape went to $back, not the screen the settings were opened over" }

        context.restoreDefaultGameOptions()
        context.runOnClient<Nothing> { it.resizeGui() }
        context.input.resizeWindow(window.first, window.second)
    }

    /**
     * Drives each kind of row against the config: a press, a drag and the wheel on a number, its field, the keyboard,
     * the changed marker, a choice's menu and steps, a pick's menu of looks, keys, a group's switch, a fold, a span,
     * and a search that finds an option inside a choice. Then a change survives closing and reopening the screen.
     */
    private fun rows(context: ClientGameTestContext) {
        val g = { context.computeOnClient<io.github.noxaur.dsmod.core.config.DsModConfig, Nothing> { DsModClient.settings } }
        fun move(x: Int, y: Int) {
            context.input.setCursorPos(x * 2.0, y * 2.0)
            context.waitTick()
        }
        fun click(x: Int, y: Int, button: Int = 0) {
            move(x, y)
            context.input.pressMouse(button)
            context.waitTicks(2)
        }
        fun key(key: Int, shift: Boolean = false) {
            if (shift) context.input.holdShift()
            context.input.pressKey(key)
            if (shift) context.input.releaseShift()
            context.waitTicks(2)
        }
        fun spot(id: String): SettingRows.Spot {
            val spot = context.computeOnClient<SettingRows.Spot?, Nothing> { (it.gui.screen() as SettingsScreen).let { s -> s.reveal(id); s.spot(id) } }
            context.waitTicks(2)
            return context.computeOnClient<SettingRows.Spot?, Nothing> { (it.gui.screen() as SettingsScreen).spot(id) } ?: error("No row $id on the screen, after $spot")
        }
        fun <T> expect(what: String, want: T, read: (io.github.noxaur.dsmod.core.config.DsModConfig) -> T) {
            val got = context.computeOnClient<T, Nothing> { read(DsModClient.settings) }
            check(got == want) { "$what: $got, not $want" }
        }

        // Camera: numbers, a signed one, a choice, keys and two heading switches.
        context.runOnClient<Nothing> { DsModClient.settings.general.cameraTilt = -12f }
        val cameraTabs = SettingsTabs(Place(5))
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
        open(context, SettingsTabs(Place(2)))
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

        // The End: a span under Boss Beacon, drawn by a press and drag on its empty track.
        open(context, SettingsTabs(Place(0)))
        val beacon = spot("bossBeacon")
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
        open(context, SettingsTabs(Place(0)))
        context.input.typeChars("rngesus")
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot("lootAlertTier") != null }) { "The search for a choice's value shows no Alert Tier" }
        context.takeScreenshot("own-settings-search-option")

        // An action opens its editor, which returns to the settings.
        open(context, SettingsTabs(Place(6)))
        val edit = spot("editBinds")
        click(edit.controlX + 4, edit.controlY + 8)
        val opened = context.computeOnClient<String, Nothing> { it.gui.screen()?.javaClass?.simpleName ?: "nothing" }
        check(opened == "CommandBindsScreen") { "Edit Binds opened $opened" }
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is SettingsScreen }) { "The binds editor did not return to the settings" }

        open(context, SettingsTabs(Place(8)))
        val requests = spot("requests")
        click(requests.controlX + 4, requests.controlY + 8)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen()?.javaClass?.simpleName == "ContributeScreen" }) { "Requests did not open the Contribute screen" }
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is SettingsScreen }) { "The Contribute screen did not return to the settings" }

        // A change is written when the screen closes, and is there when it opens again.
        open(context, SettingsTabs(Place(8)))
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
        open(context, SettingsTabs(Place(8)))
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

    /**
     * Hovers a row of each kind and checks what the narrator says of it: its name, kind and value, that it is changed
     * from its default, that a switch dims it, and its new value after a change. The sidebar and the tab bar say
     * what is under the mouse too. Then a search paints the words it matched, in both themes.
     */
    private fun narration(context: ClientGameTestContext) {
        fun move(x: Int, y: Int) {
            context.input.setCursorPos(x * 2.0, y * 2.0)
            context.waitTick()
        }
        fun spot(id: String): SettingRows.Spot {
            context.runOnClient<Nothing> { (it.gui.screen() as SettingsScreen).reveal(id) }
            context.waitTicks(2)
            return context.computeOnClient<SettingRows.Spot?, Nothing> { (it.gui.screen() as SettingsScreen).spot(id) } ?: error("No row $id on the screen")
        }
        fun over(id: String, want: String) {
            val at = spot(id)
            move(at.x + 20, at.y + at.height / 2)
            hear(context, want, "Over $id")
        }

        open(context, SettingsTabs(Place(5)))
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
        open(context, SettingsTabs(Place(5)))
        over("cameraDistance", "Distance, number, 4.5, changed from 4.0, dimmed, Camera is off")
        context.runOnClient<Nothing> {
            DsModClient.settings.general.camera = true
            DsModClient.settings.general.cameraDistance = 4f
        }

        open(context, SettingsTabs(Place(2)))
        over("rarityPreset", "Style, pick, Gilded, 2 of 4")
        // The rows opened it, and what is open lasts as long as the tabs do.
        over("customLook", "Custom Look, fold, expanded")
        over("slotLock", "Slot Lock, switch, on, collapsed")

        open(context, SettingsTabs(Place(0)))
        if (context.computeOnClient<Boolean, Nothing> { (it.gui.screen() as SettingsScreen).spot("beamSections") == null }) {
            val beacon = spot("bossBeacon")
            move(beacon.extraX + 7, beacon.controlY + 6)
            context.input.pressMouse(0)
            context.waitTicks(2)
        }
        over("bossBeacon", "Boss Beacon, switch, on, expanded")
        over("beamSections", "Beam Timing, span, 175 to 200")

        open(context, SettingsTabs(Place(7)))
        over("moveHud", "Move HUD, button, Move")
        open(context, SettingsTabs(Place(8)))
        over("fullbright", "Fullbright, switch, off")
        // The sidebar's lines start 24 down, and the bar's first tab 4 in from the sidebar's 116.
        move(30, 24 + 8)
        hear(context, "The End, section, collapsed", "Over The End in the sidebar")
        move(120 + 30, 9)
        hear(context, "General, tab, selected", "Over the tab")

        // A search paints the words it matched: in names and descriptions, in a fold's name, and in a dropdown's value.
        open(context, SettingsTabs(Place(0)))
        context.input.typeChars("gilded look")
        context.waitTicks(2)
        context.takeScreenshot("own-settings-search-marks")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-search-marks-vanilla")
        theme(context, Theme.CHARCOAL)
    }

    /** Checks that the narrator would say [want] now, as vanilla collects what the screen narrates. */
    private fun hear(context: ClientGameTestContext, want: String, step: String) {
        val said = context.computeOnClient<String, Nothing> { (it.gui.screen() as SettingsScreen).narration() }
        check(want in said) { "$step, the narrator says \"$said\", not \"$want\"" }
    }

    private fun open(context: ClientGameTestContext, tabs: SettingsTabs) {
        context.setScreen { SettingsScreen(TitleScreen(), tabs) }
        context.waitTicks(2)
    }

    /** Checks the tabs' titles from the left, with the selected one in brackets. */
    private fun expect(context: ClientGameTestContext, tabs: SettingsTabs, titles: String, step: String) {
        val shown = context.computeOnClient<String, Nothing> {
            tabs.tabs.withIndex().joinToString(" ") { (i, tab) -> tab.title(names).let { if (i == tabs.selected) "[$it]" else it } }
        }
        check(shown == titles) { "$step: the tabs are $shown, not $titles" }
    }

    private fun theme(context: ClientGameTestContext, theme: Theme) {
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = theme }
        context.waitTicks(2)
    }

    private fun guiScale(context: ClientGameTestContext, scale: Int) {
        context.runOnClient<Nothing> { client ->
            client.options.guiScale().set(scale)
            client.resizeGui()
        }
        context.waitTicks(2)
    }
}
