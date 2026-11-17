package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.player.ModelPose
import io.github.noxaur.dsmod.core.player.ModelTarget

/**
 * Resizes, tilts, spins and poses player models, as only this player sees them. It reads nothing from the server, so
 * all of it is `PlayerModels` in the edge, with the rules in `core.player`. The edge reads these settings each frame.
 */
class PlayerModel(config: DsModConfig) :
    Feature(config, "player-model", Area.ANYWHERE, Store.GENERAL, "Resize, tilt, spin and pose players, as only you see them.", default = false) {
    init {
        preview("modelPreview", "Preview", "Drag your model to turn it, and see the settings below on it.", Canvas.PLAYER_MODEL)
    }

    val target = choice("modelTarget", "Players", "Change only your own model, or every player's.", ModelTarget.SELF)
    val size = scale("modelSize", "Size", "Grow or shrink the model.")
    val width = scale("modelWidth", "Width", "Make the model wider, or thinner below 1.")
    val height = scale("modelHeight", "Height", "Make the model taller, or shorter below 1.")
    val depth = scale("modelDepth", "Depth", "Make the model deeper, or flatter below 1.")
    private val stretch = fold("modelStretch", "Stretch", "Make the model wider, taller or deeper than its size.", width, height, depth)
    val tiltForward = number("modelTiltForward", "Tilt Forward", "Lean the model forward, or back below zero, in degrees.", 0f, -180f..180f, step = 1f, unit = "°")
    val tiltSideways = number("modelTiltSideways", "Tilt Sideways", "Lean the model to its right, or left below zero, in degrees.", 0f, -180f..180f, step = 1f, unit = "°")
    val upsideDown = switch("modelUpsideDown", "Upside Down", "Stand the model on its head.", default = false)

    /** Turns a second, the other way below zero. */
    val spin = number("modelSpin", "Spin", "Spin the model this many turns a second, the other way below zero.", 0f, -5f..5f, step = 0.1f, unit = "/s")
    val pose = choice("modelPose", "Pose", "Hold the model's arms and legs still in a pose.", ModelPose.GAME)

    init {
        action("resetPlayerModel", "Reset Model", "Put every Player Model setting back to the game's own.", button = "Reset", run = ::reset)
    }

    /** Puts every setting but the switch and whose model back to its default. */
    fun reset() = rows.forEach { if (it !== target) it.reset() }

    private fun scale(key: String, name: String, description: String) = number(key, name, description, 1f, 0.1f..3f, step = 0.05f, unit = "x")
}
