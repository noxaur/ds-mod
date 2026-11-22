package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.HudEditorScreen
import io.github.noxaur.dsmod.gametest.InventoryGrid.ID
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class HudKeysTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) {
        val hud = DsModClient.settings.hud
        val guiScale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
        fun pixels() = context.computeOnClient<Int, Nothing> { (checkNotNull(Hud.find(ID)).scale * guiScale).roundToInt() }
        try {
            hud.editKey.value = GLFW.GLFW_KEY_J
            context.input.pressKey(GLFW.GLFW_KEY_J)
            context.waitForScreen(HudEditorScreen::class.java)

            val block = context.computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(ID)) }
            context.click(block.x + 5.0, block.y + 5.0)
            val before = pixels()
            context.input.pressKey(GLFW.GLFW_KEY_EQUAL)
            context.waitTick()
            check(pixels() == before + 1) { "+ did not make the grid bigger: ${pixels()} pixels, not ${before + 1}" }
            context.input.pressKey(GLFW.GLFW_KEY_MINUS)
            context.waitTick()
            check(pixels() == before) { "- did not make the grid smaller: ${pixels()} pixels, not $before" }
            context.setScreen { null }
        } finally {
            hud.editKey.value = -1
            hud.positions.value -= ID
        }
    }
}
