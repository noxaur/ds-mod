package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.PlayerModel
import io.github.noxaur.dsmod.core.player.ModelPose
import io.github.noxaur.dsmod.core.player.ModelTarget
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.PlayerModels
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.gametest.SettingsSteps.at
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import net.minecraft.client.renderer.entity.player.AvatarRenderer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.util.Mth
import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * The player seen from the front in third person, with a mannequin beside them: each Player Model setting changes the
 * frame by more than the idle sway of the arms does, Only Me leaves the mannequin as the game draws it, and first
 * person stays the game's own to the pixel. Read the screenshots.
 */
class PlayerModelTest : FabricClientGameTest {
    /** How many pixels differ by more than the shade of one or two the models' lighting flickers by between frames. */
    private fun moved(a: BufferedImage, b: BufferedImage) = diff(a, b, tolerance = 8)

    override fun runTest(context: ClientGameTestContext) {
        val playerModel = DsModClient.feature<PlayerModel>()
        fun shot(name: String): BufferedImage = image(context.takeScreenshot(name))

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
                    playerModel.feature.enabled = false
                    val vanilla = region(shot("$name-off"))
                    playerModel.feature.enabled = true
                    return moved(vanilla, region(shot(name)))
                }
                fun differs(what: String) = against(what).let { check(it > still) { "$what changed only $it pixels" } }

                against("Defaults").let { check(it < still) { "Player Model at its defaults changed $it pixels" } }
                playerModel.size.value = 2f
                differs("Size")
                playerModel.size.value = 1f
                playerModel.width.value = 2f
                differs("Width")
                playerModel.width.value = 1f
                playerModel.height.value = 0.5f
                differs("Height")
                playerModel.height.value = 1f
                playerModel.tiltForward.value = 45f
                differs("Tilt Forward")
                playerModel.tiltForward.value = 0f
                playerModel.tiltSideways.value = 30f
                differs("Tilt Sideways")
                playerModel.tiltSideways.value = 0f
                playerModel.upsideDown.value = true
                differs("Upside Down")
                playerModel.upsideDown.value = false
                // Flat on the ground with the arms ahead, the way the settings combine.
                playerModel.tiltForward.value = 90f
                playerModel.pose.value = ModelPose.ARMS_UP
                differs("Flat")
                playerModel.tiltForward.value = 0f
                for (pose in ModelPose.entries - ModelPose.GAME) {
                    playerModel.pose.value = pose
                    differs("Pose ${pose.name.lowercase().replace('_', '-')}")
                }

                // Player Model only draws. The player's own turn, look, head and place, which the game sends the server,
                // are the same with every setting at its most as with the feature off.
                fun body() = context.computeOnClient<List<Double>, Nothing> { client ->
                    val p = client.player!!
                    listOf(p.yRot, p.xRot, p.yHeadRot, p.yBodyRot).map(Float::toDouble) + listOf(p.x, p.y, p.z)
                }
                playerModel.feature.enabled = false
                context.waitTicks(5)
                val alone = body()
                playerModel.feature.enabled = true
                playerModel.target.value = ModelTarget.EVERYONE
                playerModel.size.value = 3f
                playerModel.tiltForward.value = 90f
                playerModel.tiltSideways.value = -45f
                playerModel.upsideDown.value = true
                playerModel.spin.value = 5f
                playerModel.pose.value = ModelPose.DAB
                context.waitTicks(40)
                check(body() == alone) { "Player Model moved or turned the player: ${body()}, not $alone" }
                playerModel.reset()
                playerModel.target.value = ModelTarget.SELF

                // A spin moves the model between two frames a few ticks apart. A T-pose holds the arms still.
                playerModel.pose.value = ModelPose.T_POSE
                playerModel.spin.value = 0.5f
                val spinning = shot("player-model-spin")
                context.waitTicks(5)
                moved(spinning, shot("player-model-spin-later")).let { check(it > still) { "Spin moved only $it pixels" } }

                // While it spins, the model faces where the spin says as it is drawn, however the body is turned, and the
                // head turns with it. Without a spin both follow the player.
                fun facing(body: Float) = context.computeOnClient<Pair<Float, Float>, Nothing> { client ->
                    val player = client.player!!
                    player.yBodyRot = body
                    player.yBodyRotO = body
                    player.yHeadRot = body + 40f
                    player.yHeadRotO = body + 40f
                    val renderer = client.entityRenderDispatcher.getRenderer(player)
                    val state = renderer.createRenderState(player, 1f) as AvatarRenderState
                    val model = (renderer as AvatarRenderer<*>).model
                    model.setupAnim(state)
                    PlayerModels.facing(state, state.bodyRot) to model.head.yRot
                }
                playerModel.spin.value = 0.1f
                val (ahead, head) = facing(0f)
                val (turned, _) = facing(90f)
                check(abs(Mth.wrapDegrees(turned - ahead)) < 1f) { "Turning the body 90 degrees turned the spinning model from $ahead to $turned" }
                check(head == 0f) { "The spinning model's head turned $head radians from its body" }
                playerModel.spin.value = 0f
                val (followed, followedHead) = facing(0f)
                check(abs(Mth.wrapDegrees(facing(90f).first - followed - 90f)) < 1f) { "Without a spin the model did not follow the body" }
                check(followedHead != 0f) { "Without a spin the head did not follow the player's look" }
                playerModel.pose.value = ModelPose.GAME

                // The mannequin stands on the right of the frame. Only Me leaves it as the game draws it, and
                // Everyone shrinks it too.
                val mannequin = { image: BufferedImage -> image.getSubimage(image.width * 2 / 3, 0, image.width / 3, image.height) }
                playerModel.size.value = 0.5f
                against("Only Me", mannequin).let { check(it < still) { "Only Me changed the mannequin by $it pixels" } }
                playerModel.target.value = ModelTarget.EVERYONE
                against("Everyone", mannequin).let { check(it > still) { "Everyone changed the mannequin by only $it pixels" } }
                playerModel.target.value = ModelTarget.SELF

                // The inventory draws the same model.
                playerModel.size.value = 1f
                playerModel.pose.value = ModelPose.STAR
                playerModel.tiltSideways.value = 20f
                context.openInventory()
                context.waitTicks(2)
                context.takeScreenshot("player-model-inventory")
                // The inventory turns the model toward the mouse, and a spin still turns it there. The mouse stays put.
                val box = context.computeOnClient<IntArray, Nothing> { client ->
                    val k = client.window.guiScale.toInt()
                    val left = (client.window.guiScaledWidth - 176) / 2
                    val top = (client.window.guiScaledHeight - 166) / 2
                    intArrayOf((left + 26) * k, (top + 8) * k, 49 * k, 70 * k)
                }
                fun model(name: String) = shot(name).getSubimage(box[0], box[1], box[2], box[3])
                playerModel.pose.value = ModelPose.T_POSE
                playerModel.tiltSideways.value = 0f
                context.waitTicks(2)
                val held = moved(model("player-model-inventory-still"), model("player-model-inventory-still-later"))
                check(held < 50) { "With the mouse still and no spin, the inventory's model moved $held pixels" }
                playerModel.spin.value = 0.5f
                val spinStart = model("player-model-inventory-spin")
                context.waitTicks(5)
                val spun = moved(spinStart, model("player-model-inventory-spin-later"))
                check(spun > 500) { "Spin turned the inventory's model by only $spun pixels" }
                playerModel.spin.value = 0f
                // The rows, with the changed ones marked.
                context.setScreen { SettingsScreen(null, SettingsTabs(at("General")), reveal = "resetPlayerModel") }
                context.waitTicks(2)
                context.takeScreenshot("player-model-settings")
                context.setScreen { null }

                // First person draws no model, and its hand is drawn apart from the model, so nothing changes there.
                context.runOnClient<Nothing> { it.options.cameraType = CameraType.FIRST_PERSON }
                context.input.lookAt(0f, 90f)
                playerModel.feature.enabled = false
                // The arm trails a turn of the camera for a while.
                context.waitFor { val p = it.player!!; p.xBob == p.xRot && p.xBobO == p.xRot && p.yBob == p.yRot && p.yBobO == p.yRot }
                val first = shot("player-model-first-person")
                playerModel.feature.enabled = true
                playerModel.size.value = 3f
                playerModel.tiltForward.value = 60f
                playerModel.upsideDown.value = true
                playerModel.spin.value = 2f
                playerModel.pose.value = ModelPose.ZOMBIE
                context.waitTicks(2)
                // A block on ds-mod's HUD, such as a cooldown an earlier test started, may count down between the frames.
                val hud = context.computeOnClient<List<IntArray>, Nothing> { client ->
                    val k = client.window.guiScale.toFloat()
                    Hud.placed().map { intArrayOf(((it.x - 4) * k).toInt(), ((it.y - 4) * k).toInt(), ((it.x + it.width * it.scale + 4) * k).toInt(), ((it.y + it.height * it.scale + 4) * k).toInt()) }
                }
                val on = shot("player-model-first-person-on")
                val outside = diff(first, on) { x, y -> hud.none { x >= it[0] && x < it[2] && y >= it[1] && y < it[3] } }
                check(outside == 0) { "Player Model changed first person by $outside pixels" }

                playerModel.reset()
                check(playerModel.size.value == 1f && playerModel.pose.value == ModelPose.GAME) { "Reset left the model changed" }
            } finally {
                playerModel.feature.enabled = false
                playerModel.target.value = ModelTarget.SELF
                playerModel.reset()
                context.runOnClient<Nothing> {
                    it.options.vignette().set(vignette)
                    it.options.cameraType = CameraType.FIRST_PERSON
                }
            }
        }
    }
}
