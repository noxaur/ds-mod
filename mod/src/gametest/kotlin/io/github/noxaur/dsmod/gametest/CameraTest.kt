package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.camera.ThirdPerson
import io.github.noxaur.dsmod.core.camera.Views
import io.github.noxaur.dsmod.core.feature.visuals.camera.thirdperson.Camera
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import kotlin.math.abs

class CameraTest : FabricClientGameTest {
    private class Shot(val distance: Double, val rise: Double, val pitch: Float, val head: Float)

    override fun runTest(context: ClientGameTestContext) {
        val camera = DsModClient.feature<Camera>()

        fun shot() = context.computeOnClient<Shot, Nothing> { client ->
            val camera = client.gameRenderer.mainCamera()
            val eyes = client.player!!.eyePosition
            Shot(camera.position().distanceTo(eyes), camera.position().y - eyes.y, camera.xRot(), client.player!!.xRot)
        }
        fun view() = context.computeOnClient<CameraType, Nothing> { it.options.cameraType }
        fun view(type: CameraType) {
            context.runOnClient<Nothing> { it.options.cameraType = type }
            context.waitTicks(5)
        }
        fun f5(): CameraType {
            context.input.pressKey { it.keyTogglePerspective }
            context.waitTicks(3)
            return view()
        }
        fun near(got: Double, want: Double, what: String) = check(abs(got - want) < 0.15) { "$what is $got, not $want" }

        context.worldBuilder().create().use { world ->
            try {
                context.waitTicks(40)
                context.input.lookAt(0f, 0f)
                view(CameraType.THIRD_PERSON_BACK)
                near(shot().distance, 4.0, "The game's own distance")

                camera.distance.value = 10f
                context.waitTicks(5)
                near(shot().distance, 10.0, "The distance set to 10")
                context.takeScreenshot("camera-distance")

                camera.feature.enabled = false
                context.waitTicks(5)
                near(shot().distance, 4.0, "The distance with Camera off")
                camera.feature.enabled = true

                camera.height.value = 2f
                context.waitTicks(5)
                near(shot().rise, 2.0, "The rise set to 2")
                context.takeScreenshot("camera-height")

                camera.height.value = 3f
                world.server.runCommand("execute at @a run setblock ~ ~3 ~ stone")
                context.waitTicks(10)
                val under = shot().rise
                check(under > 0.5 && under < 1.3) { "Under a block the camera rose $under" }
                world.server.runCommand("execute at @a run setblock ~ ~3 ~ air")
                camera.height.value = 0f

                camera.tilt.value = 20f
                context.waitTicks(5)
                shot().let {
                    near(it.pitch.toDouble(), 20.0, "The camera's pitch tilted by 20")
                    near(it.head.toDouble(), 0.0, "The player's own pitch under a tilt")
                    check(it.rise > 3) { "A tilt down should lift the camera behind the player, and it rose ${it.rise}" }
                }
                context.takeScreenshot("camera-tilt")
                view(CameraType.FIRST_PERSON)
                near(shot().pitch.toDouble(), 0.0, "The first-person pitch under a tilt")
                camera.tilt.value = 0f

                camera.views.value = Views.SKIP_FRONT
                check(f5() == CameraType.THIRD_PERSON_BACK) { "F5 from first person went to ${view()}" }
                check(f5() == CameraType.FIRST_PERSON) { "F5 did not skip the front view, and went to ${view()}" }
                camera.views.value = Views.SKIP_BACK
                check(f5() == CameraType.THIRD_PERSON_FRONT) { "F5 did not skip the back view, and went to ${view()}" }
                check(f5() == CameraType.FIRST_PERSON) { "F5 from the front view went to ${view()}" }
                camera.views.value = Views.ALL

                camera.smoothing.value = 100f
                context.input.pressKey { it.keyTogglePerspective }
                context.waitTicks(2)
                val sliding = shot().distance
                check(sliding > 0.5 && sliding < 9) { "Two ticks into the slide the camera is $sliding away" }
                context.waitTicks(40)
                near(shot().distance, 10.0, "The distance after the slide")
            } finally {
                camera.distance.value = ThirdPerson.DISTANCE
                camera.height.value = 0f
                camera.tilt.value = 0f
                camera.smoothing.value = 0f
                camera.views.value = Views.ALL
                camera.feature.enabled = true
                context.runOnClient<Nothing> { it.options.cameraType = CameraType.FIRST_PERSON }
            }
        }
    }
}
