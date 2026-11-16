package io.github.noxaur.dsmod.core.player

import io.github.noxaur.dsmod.core.config.DsModConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelShapeTest {
    private val second = 1_000_000_000L

    @Test
    fun `no spin stays facing ahead`() {
        assertEquals(0f, ModelShape.spin(123 * second, 0f))
    }

    @Test
    fun `a spin goes round once per turn and starts again from zero`() {
        assertEquals(90f, ModelShape.spin(second / 4, 1f), 0.01f)
        assertEquals(180f, ModelShape.spin(second / 4, 2f), 0.01f)
        assertEquals(90f, ModelShape.spin(5 * second + second / 4, 1f), 0.01f)
    }

    @Test
    fun `a spin below zero goes the other way, still within one turn`() {
        assertEquals(270f, ModelShape.spin(second / 4, -1f), 0.01f)
    }

    @Test
    fun `a spin stays smooth after the computer has been on for weeks`() {
        val weeks = 3L * 7 * 24 * 3600 * second
        assertEquals(36f, ModelShape.spin(weeks + second / 10, 1f), 0.01f)
    }

    @Test
    fun `the default pose leaves the game's own, and every other holds all four limbs`() {
        assertNull(ModelPose.GAME.limbs)
        assertEquals(ModelPose.GAME, DsModConfig().general.modelPose)
        for (pose in ModelPose.entries - ModelPose.GAME) checkNotNull(pose.limbs) { "$pose holds no limbs" }
    }

    @Test
    fun `reset puts the shape back and keeps the switch and whose model`() {
        val g = DsModConfig().general
        g.playerModel = true
        g.modelTarget = ModelTarget.EVERYONE
        g.modelSize = 2f
        g.modelWidth = 0.5f
        g.modelHeight = 1.5f
        g.modelDepth = 0.2f
        g.modelTiltForward = 30f
        g.modelTiltSideways = -10f
        g.modelUpsideDown = true
        g.modelSpin = 1f
        g.modelPose = ModelPose.T_POSE
        g.resetPlayerModel()
        val fresh = DsModConfig().general
        assertEquals(true, g.playerModel)
        assertEquals(ModelTarget.EVERYONE, g.modelTarget)
        assertEquals(
            listOf(fresh.modelSize, fresh.modelWidth, fresh.modelHeight, fresh.modelDepth, fresh.modelTiltForward, fresh.modelTiltSideways, fresh.modelSpin),
            listOf(g.modelSize, g.modelWidth, g.modelHeight, g.modelDepth, g.modelTiltForward, g.modelTiltSideways, g.modelSpin),
        )
        assertEquals(false, g.modelUpsideDown)
        assertEquals(ModelPose.GAME, g.modelPose)
    }
}
