package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.TabEditorScreen
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW

/**
 * Opens the tab editor from the settings and drives it: drags a tab to the top, cancels a drag with Escape, adds a
 * tab on an empty place and types its command and item, moves it with an arrow key and deletes it. Read the
 * screenshots in both themes: the menu with its tabs, the free places and the form.
 */
class TabEditorTest : FabricClientGameTest {
    private fun screen(context: ClientGameTestContext) = context.computeOnClient<TabEditorScreen, Nothing> { it.gui.screen() as TabEditorScreen }

    private fun moveTo(context: ClientGameTestContext, at: Pair<Int, Int>) {
        val scale = context.computeOnClient<Double, Nothing> { it.window.guiScale.toDouble() }
        context.input.setCursorPos(at.first * scale, at.second * scale)
        context.waitTicks(1)
    }

    private fun press(at: Pair<Int, Int>) = MouseButtonEvent(at.first.toDouble(), at.second.toDouble(), MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0))

    private fun tabs(context: ClientGameTestContext) = context.computeOnClient<List<io.github.noxaur.dsmod.core.ui.MenuTab>, Nothing> { DsModClient.settings.betterUi.tabs }

    override fun runTest(context: ClientGameTestContext) {
        context.runOnClient<Nothing> { DsModClient.settings.betterUi.tabs = TabLayout.DEFAULT }
        try {
            context.runOnClient<Nothing> { DsModClient.settings.betterUi.editTabs.run() }
            context.waitFor { it.gui.screen() is TabEditorScreen }
            context.waitTicks(2)
            context.takeScreenshot("tab-editor")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTicks(2)
            context.takeScreenshot("tab-editor-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

            // Drag Storage from the bottom-left to the top-left. The test input sends no drags, so the screen gets them here.
            val editor = screen(context)
            val from = editor.placeAt(TabEdge.BOTTOM, 0)
            val to = editor.placeAt(TabEdge.TOP, 0)
            context.runOnClient<Nothing> {
                editor.mouseClicked(press(from), false)
                for (step in 1..4) editor.mouseDragged(press(from.first + (to.first - from.first) * step / 4 to from.second + (to.second - from.second) * step / 4), 0.0, 0.0)
            }
            moveTo(context, to)
            context.takeScreenshot("tab-editor-dragging")
            context.runOnClient<Nothing> { editor.mouseReleased(press(to)) }
            context.waitTicks(1)
            val storage = tabs(context).first { it.command == "storage" }
            check(storage.edge == TabEdge.TOP && storage.slot == 0) { "Storage was dropped at ${storage.edge} ${storage.slot}" }

            // Escape during a drag puts the tab back.
            context.runOnClient<Nothing> {
                editor.mouseClicked(press(editor.placeAt(TabEdge.BOTTOM, 1)), false)
                editor.mouseDragged(press(editor.placeAt(TabEdge.BOTTOM, 4)), 0.0, 0.0)
            }
            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.runOnClient<Nothing> { editor.mouseReleased(press(editor.placeAt(TabEdge.BOTTOM, 4))) }
            context.waitTicks(1)
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() is TabEditorScreen }) { "Escape during a drag closed the editor" }
            check(tabs(context).first { it.command == "ec" }.slot == 1) { "Escape did not put the dragged tab back" }

            // A click on an empty place adds a tab there, and the form takes what is typed.
            moveTo(context, editor.placeAt(TabEdge.BOTTOM, 5))
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(1)
            context.input.typeChars("/pets")
            context.runOnClient<Nothing> { client ->
                val screen = client.gui.screen() as TabEditorScreen
                val item = screen.children().filterIsInstance<KitTextField>()[1]
                screen.focused = item
                item.value = ""
            }
            context.input.typeChars("bone")
            context.input.pressKey(GLFW.GLFW_KEY_ENTER)
            context.waitTicks(2)
            val pets = tabs(context).last()
            check(pets.edge == TabEdge.BOTTOM && pets.slot == 5 && pets.command == "pets" && pets.item == "minecraft:bone") { "The new tab is $pets" }
            context.takeScreenshot("tab-editor-added")

            context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
            context.waitTicks(1)
            check(tabs(context).last().slot == 6) { "Right did not move the tab" }
            context.input.pressKey(GLFW.GLFW_KEY_DELETE)
            context.waitTicks(1)
            check(tabs(context).none { it.command == "pets" }) { "Delete did not remove the tab" }

            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.waitTicks(2)
            check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() !is TabEditorScreen }) { "Escape did not close the editor" }
        } finally {
            context.runOnClient<Nothing> { DsModClient.settings.betterUi.tabs = TabLayout.DEFAULT }
        }
    }
}
