package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.feature.general.FreeLook
import io.github.noxaur.dsmod.edge.Cameras
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import org.lwjgl.glfw.GLFW

class FreeLookTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val freeLook = DsModClient.feature<FreeLook>()
        val key = GLFW.GLFW_KEY_LEFT_ALT

        fun head() = context.computeOnClient<Pair<Float, Float>, Nothing> { it.player!!.yRot to it.player!!.xRot }
        fun camera() = context.computeOnClient<Pair<Float, Float>, Nothing> { it.gameRenderer.mainCamera().let { camera -> camera.yRot() to camera.xRot() } }
        fun view() = context.computeOnClient<CameraType, Nothing> { it.options.cameraType }
        fun mouse(x: Double, y: Double) {
            context.input.moveCursor(x, y)
            context.waitTicks(3)
        }

        context.worldBuilder().create().use {
            try {
                context.waitTicks(40)
                context.input.lookAt(0f, 0f)
                context.waitTicks(3)
                // @keep The game turns the player only while it holds the mouse, and it drops the first move after taking it.
                context.runOnClient<Nothing> { it.mouseHandler.grabMouse() }
                mouse(10.0, 0.0)
                mouse(100.0, 0.0)
                val facing = head()
                check(facing.first != 0f) { "The mouse does not turn the player in this test, so it proves nothing" }

                context.input.holdKey(key)
                context.input.moveCursor(400.0, 100.0)
                context.waitTicks(1)
                check(head() == facing) { "The press frame turned the player from $facing to ${head()}" }
                check(Cameras.looking && view() == CameraType.THIRD_PERSON_BACK) { "Holding the key gave ${view()}, looking ${Cameras.looking}" }
                mouse(400.0, 100.0)
                check(head() == facing) { "Free Look turned the player from $facing to ${head()}" }
                camera().let { check(it.first > facing.first + 20 && it.second > 5) { "The camera stayed at $it" } }
                context.takeScreenshot("free-look")
                context.input.releaseKey(key)
                context.waitTicks(3)
                check(!Cameras.looking && view() == CameraType.FIRST_PERSON) { "The release left ${view()}, looking ${Cameras.looking}" }
                check(head() == facing && camera() == facing) { "After the release the player faces ${head()} and the camera ${camera()}" }

                context.runOnClient<Nothing> { it.options.cameraType = CameraType.THIRD_PERSON_FRONT }
                context.input.holdKey(key)
                context.waitTicks(3)
                check(Cameras.looking && view() == CameraType.THIRD_PERSON_FRONT) { "From the front view the key gave ${view()}" }
                context.input.pressKey { it.keyTogglePerspective }
                context.waitTicks(3)
                check(!Cameras.looking && view() == CameraType.FIRST_PERSON) { "F5 to first person left looking ${Cameras.looking} in ${view()}" }
                context.waitTicks(5)
                check(!Cameras.looking) { "The held key started Free Look again" }
                context.input.releaseKey(key)
                context.waitTicks(3)

                freeLook.mode.value = KeyMode.TOGGLE
                context.input.pressKey(key)
                context.waitTicks(5)
                check(Cameras.looking) { "A press in toggle mode did not start Free Look" }
                mouse(200.0, 0.0)
                check(head() == facing) { "Toggled Free Look turned the player" }
                context.input.pressKey(key)
                context.waitTicks(5)
                check(!Cameras.looking && view() == CameraType.FIRST_PERSON) { "A second press left looking ${Cameras.looking} in ${view()}" }
                freeLook.mode.value = KeyMode.HOLD

                context.openChat()
                context.input.holdKey(key)
                context.waitTicks(3)
                check(!Cameras.looking) { "The key started Free Look with the chat open" }
                context.input.releaseKey(key)
                context.runOnClient<Nothing> { it.gui.setScreen(null) }
                context.waitTicks(3)

                freeLook.feature.enabled = false
                context.input.holdKey(key)
                context.waitTicks(3)
                check(!Cameras.looking && view() == CameraType.FIRST_PERSON) { "The key works with Free Look off" }
                context.input.releaseKey(key)
                context.waitTicks(3)
            } finally {
                context.input.releaseKey(key)
                freeLook.feature.enabled = true
                freeLook.mode.value = KeyMode.HOLD
                context.runOnClient<Nothing> { it.options.cameraType = CameraType.FIRST_PERSON }
            }
        }
    }
}
