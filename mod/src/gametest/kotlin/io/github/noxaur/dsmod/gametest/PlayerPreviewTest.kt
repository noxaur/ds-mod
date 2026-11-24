package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.playermodel.PlayerModel
import io.github.noxaur.dsmod.core.player.ModelPose
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.core.ui.Turntable
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.PlayerPreview
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.TitleScreen
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

class PlayerPreviewTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val playerModel = DsModClient.feature<PlayerModel>()
        val window = SettingsSteps.setUp(context)
        val driver = SettingsDriver(context)
        fun preview() = context.computeOnClient<PlayerPreview, Nothing> { (it.gui.screen() as SettingsScreen).editor("modelPreview") as PlayerPreview }
        fun open() {
            context.setScreen { DsModClient.settingsScreen(TitleScreen(), SettingsTabs(at("General")), reveal = "modelPreview") }
            context.waitTicks(2)
        }
        fun near(got: Float, want: Float, what: String) = check(abs(got - want) < 0.5f) { "$what is $got, not $want" }
        try {
            open()
            val empty = driver.spot("modelPreview")
            context.takeScreenshot("player-preview-no-world")
            val before = preview().yaw
            driver.click(empty.controlX + empty.controlWidth / 2, empty.controlY + empty.controlHeight / 2)
            near(preview().yaw, before, "The yaw after a press with no world")
            context.setScreen { TitleScreen() }

            context.worldBuilder().create().use {
                playerModel.feature.enabled = true
                playerModel.pose.value = ModelPose.T_POSE
                playerModel.tiltSideways.value = 15f
                context.waitTicks(20)
                open()
                val spot = driver.spot("modelPreview")
                context.shotInBothThemes("player-preview")
                playerModel.size.value = 2.5f
                context.waitTicks(2)
                context.takeScreenshot("player-preview-big")
                playerModel.size.value = 1f

                val cx = spot.controlX + spot.controlWidth / 2
                val cy = spot.controlY + spot.controlHeight / 2
                val start = preview().yaw
                driver.hold(cx, cy)
                driver.move(cx + 20, cy + 5)
                context.takeScreenshot("player-preview-drag")
                context.input.releaseMouse(0)
                context.waitTicks(2)
                near(preview().yaw, start + 20 * Turntable.DEGREES_PER_PIXEL.toFloat(), "The yaw after a drag")
                near(preview().pitch, 5 * Turntable.DEGREES_PER_PIXEL.toFloat(), "The pitch after a drag")
                val dropped = preview().yaw

                driver.hold(cx, cy)
                driver.move(cx - 30, cy)
                driver.escapeHeld()
                near(preview().yaw, dropped, "The yaw after Escape during a drag")
                driver.keptOpen()

                driver.key(GLFW.GLFW_KEY_RIGHT)
                near(preview().yaw, dropped + Turntable.KEY_YAW, "The yaw after Right")
                driver.key(GLFW.GLFW_KEY_LEFT)
                driver.key(GLFW.GLFW_KEY_LEFT)
                near(preview().yaw, dropped - Turntable.KEY_YAW, "The yaw after Right, Left, Left")
                driver.key(GLFW.GLFW_KEY_DOWN)
                driver.key(GLFW.GLFW_KEY_LEFT)
                near(preview().yaw, dropped - Turntable.KEY_YAW, "The yaw after Down took the keys to the next row")

                playerModel.feature.enabled = false
                context.waitTicks(2)
                context.takeScreenshot("player-preview-off")
                context.setScreen { null }
            }
        } finally {
            playerModel.feature.enabled = false
            playerModel.reset()
            SettingsSteps.restore(context, window)
        }
    }
}
