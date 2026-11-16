package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.player.ModelPose
import io.github.noxaur.dsmod.core.player.ModelTarget
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.HeldItemScene.Companion.changed
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.util.Mth
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * The player seen from the front in third person, with a mannequin beside them: each Player Model setting changes the
 * frame by more than the idle sway of the arms does, Only Me leaves the mannequin as the game draws it, and first
 * person stays the game's own to the pixel. Read the screenshots.
 */
class PlayerModelTest : FabricClientGameTest {
    /** How many pixels differ by more than the shade of one or two the models' lighting flickers by between frames. */
    private fun moved(a: BufferedImage, b: BufferedImage): Int {
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            if ((0..16 step 8).any { abs((p shr it and 0xFF) - (q shr it and 0xFF)) > 8 }) count++
        }
        return count
    }

    override fun runTest(context: ClientGameTestContext) {
        val settings = DsModClient.settings.general
        fun shot(name: String): BufferedImage = ImageIO.read(context.takeScreenshot(name).toFile())

        val vignette = context.computeOnClient<Boolean, Nothing> { it.options.vignette().get() }
        context.worldBuilder().create().use { world ->
            try {
                listOf(
                    "gamerule advance_time false",
                    "gamerule advance_weather false",
                    "time set noon",
                    "clear @a",
                    "execute at @a run summon mannequin ~2.5 ~ ~1 {Rotation:[0f,0f]}",
                ).forEach(world.server::runCommand)
                context.input.lookAt(0f, 0f)
                // The vignette eases toward the light for half a minute after a join and shades the whole frame.
                context.runOnClient<Nothing> {
                    it.options.vignette().set(false)
                    it.options.cameraType = CameraType.THIRD_PERSON_FRONT
                }
                context.waitTicks(40)

                // The arms sway as the player stands, by a hundred pixels or so between one frame and the next and
                // by thousands over a few seconds, so each setting is held against a frame of the game's own taken
                // just before it.
                val still = 1000
                fun against(what: String, region: (BufferedImage) -> BufferedImage = { it }): Int {
                    val name = "player-model-${what.lowercase().replace(' ', '-')}"
                    settings.playerModel = false
                    val vanilla = region(shot("$name-off"))
                    settings.playerModel = true
                    return moved(vanilla, region(shot(name)))
                }
                fun differs(what: String) = against(what).let { check(it > still) { "$what changed only $it pixels" } }

                against("Defaults").let { check(it < still) { "Player Model at its defaults changed $it pixels" } }
                settings.modelSize = 2f
                differs("Size")
                settings.modelSize = 1f
                settings.modelWidth = 2f
                differs("Width")
                settings.modelWidth = 1f
                settings.modelHeight = 0.5f
                differs("Height")
                settings.modelHeight = 1f
                settings.modelTiltForward = 45f
                differs("Tilt Forward")
                settings.modelTiltForward = 0f
                settings.modelTiltSideways = 30f
                differs("Tilt Sideways")
                settings.modelTiltSideways = 0f
                settings.modelUpsideDown = true
                differs("Upside Down")
                settings.modelUpsideDown = false
                // Flat on the ground with the arms ahead, the way the settings combine.
                settings.modelTiltForward = 90f
                settings.modelPose = ModelPose.ARMS_UP
                differs("Flat")
                settings.modelTiltForward = 0f
                for (pose in ModelPose.entries - ModelPose.GAME) {
                    settings.modelPose = pose
                    differs("Pose ${pose.name.lowercase().replace('_', '-')}")
                }

                // Player Model only draws. The player's own turn, look, head and place, which the game sends the server,
                // are the same with every setting at its most as with the feature off.
                fun body() = context.computeOnClient<List<Double>, Nothing> { client ->
                    val p = client.player!!
                    listOf(p.yRot, p.xRot, p.yHeadRot, p.yBodyRot).map(Float::toDouble) + listOf(p.x, p.y, p.z)
                }
                settings.playerModel = false
                context.waitTicks(5)
                val alone = body()
                settings.playerModel = true
                settings.modelTarget = ModelTarget.EVERYONE
                settings.modelSize = 3f
                settings.modelTiltForward = 90f
                settings.modelTiltSideways = -45f
                settings.modelUpsideDown = true
                settings.modelSpin = 5f
                settings.modelPose = ModelPose.DAB
                context.waitTicks(40)
                check(body() == alone) { "Player Model moved or turned the player: ${body()}, not $alone" }
                settings.resetPlayerModel()
                settings.modelTarget = ModelTarget.SELF

                // A spin moves the model between two frames a few ticks apart. A T-pose holds the arms still.
                settings.modelPose = ModelPose.T_POSE
                settings.modelSpin = 0.5f
                val spinning = shot("player-model-spin")
                context.waitTicks(5)
                moved(spinning, shot("player-model-spin-later")).let { check(it > still) { "Spin moved only $it pixels" } }

                // While it spins, the model faces where the spin says, however the player's body and head are turned.
                fun facing(body: Float) = context.computeOnClient<Pair<Float, Float>, Nothing> { client ->
                    val player = client.player!!
                    player.yBodyRot = body
                    player.yBodyRotO = body
                    val state = client.entityRenderDispatcher.getRenderer(player).createRenderState(player, 1f) as AvatarRenderState
                    state.bodyRot to state.yRot
                }
                settings.modelSpin = 0.01f
                val (ahead, head) = facing(0f)
                val (turned, _) = facing(90f)
                check(abs(Mth.wrapDegrees(turned - ahead)) < 1f) { "Turning the body 90 degrees turned the spinning model from $ahead to $turned" }
                check(head == 0f) { "The spinning model's head turned $head degrees from its body" }
                settings.modelSpin = 0f
                check(abs(Mth.wrapDegrees(facing(90f).first - facing(0f).first - 90f)) < 1f) { "Without a spin the model did not follow the body" }
                settings.modelPose = ModelPose.GAME

                // The mannequin stands on the right of the frame. Only Me leaves it as the game draws it, and
                // Everyone shrinks it too.
                val mannequin = { image: BufferedImage -> image.getSubimage(image.width * 2 / 3, 0, image.width / 3, image.height) }
                settings.modelSize = 0.5f
                against("Only Me", mannequin).let { check(it < still) { "Only Me changed the mannequin by $it pixels" } }
                settings.modelTarget = ModelTarget.EVERYONE
                against("Everyone", mannequin).let { check(it > still) { "Everyone changed the mannequin by only $it pixels" } }
                settings.modelTarget = ModelTarget.SELF

                // The inventory draws the same model.
                settings.modelSize = 1f
                settings.modelPose = ModelPose.STAR
                settings.modelTiltSideways = 20f
                context.setScreen { InventoryScreen(Minecraft.getInstance().player!!) }
                context.waitTicks(2)
                context.takeScreenshot("player-model-inventory")
                // The rows, with the changed ones marked.
                context.setScreen { SettingsScreen(null, SettingsTabs(at("General")), reveal = "resetPlayerModel") }
                context.waitTicks(2)
                context.takeScreenshot("player-model-settings")
                context.setScreen { null }

                // First person draws no model, and its hand is drawn apart from the model, so nothing changes there.
                context.runOnClient<Nothing> { it.options.cameraType = CameraType.FIRST_PERSON }
                context.input.lookAt(0f, 90f)
                settings.playerModel = false
                // The arm trails a turn of the camera for a while.
                context.waitFor { val p = it.player!!; p.xBob == p.xRot && p.xBobO == p.xRot && p.yBob == p.yRot && p.yBobO == p.yRot }
                val first = shot("player-model-first-person")
                settings.playerModel = true
                settings.modelSize = 3f
                settings.modelTiltForward = 60f
                settings.modelUpsideDown = true
                settings.modelSpin = 2f
                settings.modelPose = ModelPose.ZOMBIE
                context.waitTicks(2)
                check(changed(first, shot("player-model-first-person-on")) == 0) { "Player Model changed first person" }

                settings.resetPlayerModel()
                check(settings.modelSize == 1f && settings.modelPose == ModelPose.GAME) { "Reset left the model changed" }
            } finally {
                settings.playerModel = false
                settings.modelTarget = ModelTarget.SELF
                settings.resetPlayerModel()
                context.runOnClient<Nothing> {
                    it.options.vignette().set(vignette)
                    it.options.cameraType = CameraType.FIRST_PERSON
                }
            }
        }
    }
}
