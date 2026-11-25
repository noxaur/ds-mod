package io.github.noxaur.dragen.core.player

import io.github.noxaur.dragen.core.config.SettingsTree
import io.github.noxaur.dragen.core.feature.visuals.playermodel.PlayerModel
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
        assertEquals(ModelPose.GAME, SettingsTree().feature<PlayerModel>().pose.value)
        for (pose in ModelPose.entries - ModelPose.GAME) checkNotNull(pose.limbs) { "$pose holds no limbs" }
    }

    @Test
    fun `reset puts the shape back and keeps the switch and whose model`() {
        val g = SettingsTree().feature<PlayerModel>()
        g.feature.enabled = true
        g.target.value = ModelTarget.EVERYONE
        g.size.value = 2f
        g.width.value = 0.5f
        g.height.value = 1.5f
        g.depth.value = 0.2f
        g.tiltForward.value = 30f
        g.tiltSideways.value = -10f
        g.upsideDown.value = true
        g.spin.value = 1f
        g.pose.value = ModelPose.T_POSE
        g.reset()
        val fresh = SettingsTree().feature<PlayerModel>()
        assertEquals(true, g.feature.enabled)
        assertEquals(ModelTarget.EVERYONE, g.target.value)
        fun PlayerModel.shape() = listOf(size, width, height, depth, tiltForward, tiltSideways, spin).map { it.value }
        assertEquals(fresh.shape(), g.shape())
        assertEquals(false, g.upsideDown.value)
        assertEquals(ModelPose.GAME, g.pose.value)
    }
}
