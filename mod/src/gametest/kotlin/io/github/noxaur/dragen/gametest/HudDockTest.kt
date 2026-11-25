package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.feature.hud.equipmentdisplay.EquipmentDisplay
import io.github.noxaur.dragen.core.hud.HudDock
import io.github.noxaur.dragen.core.hud.HudScale
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.Hud
import io.github.noxaur.dragen.edge.HudEditorScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

private const val ID = "equipment-display"

class HudDockTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val equipmentDisplay = DragenClient.feature<EquipmentDisplay>()
        context.worldBuilder().create().use { world ->
            world.server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate")
            world.server.runCommand("item replace entity @a armor.feet with minecraft:iron_boots")
            DragenClient.settings.hud.positions.value -= ID
            equipmentDisplay.placement.value = HudDock.LEFT_OF_HOTBAR
            try {
                context.waitFor { Hud.find(ID)?.dock == HudDock.LEFT_OF_HOTBAR }
                val editor = InChat(context)
                context.runOnClient<Nothing> { DragenClient.openHudEditor() }
                context.waitForScreen(HudEditorScreen::class.java)
                val docked = editor.block(ID)
                val spot = { dock: HudDock -> context.computeOnClient<Pair<Float, Float>, Nothing> { client -> Hud.dockSpot(ID, dock, HudScale.snap(1f, client.window.guiScale))!!.let { it.x to it.y } } }

                editor.drag(ID, docked.x, docked.y - 60) { context.takeScreenshot("hud-dock-lifted") }
                val lifted = editor.block(ID)
                check(equipmentDisplay.placement.value == HudDock.FREE) { "The drag left the placement at ${equipmentDisplay.placement.value}" }
                check(lifted.dock == HudDock.FREE && abs(lifted.y - (docked.y - 60)) < 0.6) { "The block is at ${lifted.x}, ${lifted.y}, ${lifted.dock}" }
                check(abs(lifted.width * lifted.scale - docked.width * docked.scale) < 0.01f) { "The block changed size leaving the dock: ${lifted.scale} from ${docked.scale}" }

                val right = spot(HudDock.RIGHT_OF_HOTBAR)
                editor.drag(ID, right.first + 4, right.second - 3)
                check(equipmentDisplay.placement.value == HudDock.RIGHT_OF_HOTBAR) { "Dropped by the right spot, the placement is ${equipmentDisplay.placement.value}" }
                editor.block(ID).let { check(it.x == right.first && it.y == right.second) { "The docked block is at ${it.x}, ${it.y}, not $right" } }
                context.takeScreenshot("hud-dock-right")

                editor.drag(ID, right.first - 80, right.second - 80) { context.input.pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTick() }
                check(equipmentDisplay.placement.value == HudDock.RIGHT_OF_HOTBAR) { "Escape left the placement at ${equipmentDisplay.placement.value}" }

                val hudScale = DragenClient.settings.hud.scale.value
                DragenClient.settings.hud.scale.value = 3f
                try {
                    context.waitTick()
                    val big = editor.block(ID)
                    editor.move(big.x + 5.0, big.y + 5.0)
                    context.input.scroll(-1.0)
                    context.waitTick()
                    val after = editor.block(ID)
                    check(equipmentDisplay.placement.value == HudDock.RIGHT_OF_HOTBAR && after.scale == big.scale) { "A notch down at HUD scale 3 left ${equipmentDisplay.placement.value} at ${after.scale}, from ${big.scale}" }
                } finally {
                    DragenClient.settings.hud.scale.value = hudScale
                }
                context.waitTick()

                val before = editor.block(ID)
                editor.move(before.x + 5.0, before.y + 5.0)
                context.input.scroll(1.0)
                context.waitTick()
                check(equipmentDisplay.placement.value == HudDock.FREE && editor.block(ID).scale > before.scale) { "The wheel left the block ${equipmentDisplay.placement.value} at ${editor.block(ID).scale}" }

                equipmentDisplay.placement.value = HudDock.LEFT_OF_HOTBAR
                context.waitFor { Hud.find(ID)?.dock == HudDock.LEFT_OF_HOTBAR }
                val again = editor.block(ID)
                editor.move(again.x + 5.0, again.y + 5.0)
                context.input.pressMouse(1)
                context.waitTick()
                check(equipmentDisplay.placement.value == HudDock.FREE && ID !in DragenClient.settings.hud.positions.value) { "The right press left ${equipmentDisplay.placement.value}" }

                val left = spot(HudDock.LEFT_OF_HOTBAR)
                editor.drag(ID, left.first - 3, left.second + 2)
                check(equipmentDisplay.placement.value == HudDock.LEFT_OF_HOTBAR) { "The editor left the placement at ${equipmentDisplay.placement.value}" }
                context.setScreen { null }
            } finally {
                equipmentDisplay.placement.value = HudDock.FREE
                DragenClient.settings.hud.positions.value -= ID
            }
        }
    }
}
