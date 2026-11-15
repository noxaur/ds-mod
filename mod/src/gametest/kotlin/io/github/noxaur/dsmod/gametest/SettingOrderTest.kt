package io.github.noxaur.dsmod.gametest

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
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW

/**
 * Drives an ordered list on ds-mod's own settings screen, Equipment Display's pieces: it opens under its row, and an
 * entry hides and shows in its place, moves by a drag of its grip and by Alt with Up or Down, and goes back on Escape
 * during a drag. Screenshots show it closed, open, mid-drag and changed, in both themes.
 */
class SettingOrderTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = setUp(context)
        SettingsDriver(context).order()
        context.runOnClient<Nothing> {
            DsModClient.settings.general.equipmentPieces = EquipmentPiece.entries.toMutableList()
            DsModClient.saveSettings()
        }
        context.setScreen { TitleScreen() }
        restore(context, window)
    }

    private fun SettingsDriver.order() {
        open(context, SettingsTabs(at("Interface")))
        val pieces = { context.computeOnClient<List<EquipmentPiece>, Nothing> { DsModClient.settings.general.equipmentPieces.toList() } }
        fun entry(i: Int) = spot("equipmentPieces", i)
        val list = spot("equipmentPieces")
        context.takeScreenshot("own-settings-order-closed")
        click(list.x + 20, list.y + list.height / 2)
        spot("inventoryRows")
        spot("equipmentPieces")
        context.takeScreenshot("own-settings-order")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-order-vanilla")
        theme(context, Theme.CHARCOAL)
        val (helmet, chestplate, leggings, boots, pet) = EquipmentPiece.entries
        var e = entry(0)
        move(e.x + SettingRows.PAD + 1, e.y + 8)
        context.input.holdMouse(0)
        context.waitTick()
        for (dy in listOf(4, 12, 24, 36, 40)) move(e.x + SettingRows.PAD + 1, e.y + dy)
        context.takeScreenshot("own-settings-order-drag")
        context.input.releaseMouse(0)
        context.waitTicks(2)
        check(pieces() == listOf(chestplate, leggings, helmet, boots, pet)) { "A drag of Helmet's grip two lines down left ${pieces()}" }
        e = entry(2)
        move(e.x + 30, e.y + 8)
        context.input.holdMouse(0)
        context.waitTick()
        move(e.x + 30, e.y - 24)
        key(GLFW.GLFW_KEY_ESCAPE)
        context.input.releaseMouse(0)
        context.waitTicks(2)
        check(pieces() == listOf(chestplate, leggings, helmet, boots, pet)) { "Escape during a drag left ${pieces()}" }
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is SettingsScreen }) { "Escape during a drag closed the screen" }
        e = entry(4)
        click(e.controlX + 11, e.controlY + 6)
        check(pieces() == listOf(chestplate, leggings, helmet, boots)) { "A press on Pet's switch left ${pieces()}" }
        click(e.x + 30, e.y + 8)
        check(pieces() == listOf(chestplate, leggings, helmet, boots, pet)) { "A press on Pet's name did not show it in its place: ${pieces()}" }
        key(GLFW.GLFW_KEY_UP, alt = true)
        check(pieces() == listOf(chestplate, leggings, helmet, pet, boots)) { "Alt+Up on Pet left ${pieces()}" }
        key(GLFW.GLFW_KEY_SPACE)
        check(pieces() == listOf(chestplate, leggings, helmet, boots)) { "Space on the moved Pet left ${pieces()}" }
        key(GLFW.GLFW_KEY_SPACE)
        hear(context, "Pet, switch, on, 4 of 5", "On Pet after Alt+Up")
        move(entry(0).x + 30, entry(0).y + 8)
        hear(context, "Chestplate, switch, on, 1 of 5", "Over the first entry")
        context.takeScreenshot("own-settings-order-changed")
        theme(context, Theme.VANILLA)
        context.takeScreenshot("own-settings-order-changed-vanilla")
        theme(context, Theme.CHARCOAL)

    }
}
