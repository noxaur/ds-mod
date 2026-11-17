package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.ThirdPerson
import io.github.noxaur.dsmod.core.camera.Views
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Shapes the third-person view. It reads nothing from the server, so all of it is `Cameras` in the edge, with
 * the rules in `core.camera`. The edge reads these settings each frame.
 */
class Camera(config: DsModConfig) :
    Feature(config, "camera", Area.ANYWHERE, Store.GENERAL, "Shape the third-person view with the settings below.") {
    val distance = number("cameraDistance", "Distance", "Set how many blocks the third-person camera sits from you.", ThirdPerson.DISTANCE, 1f..50f, step = 0.5f)
    val height = number("cameraHeight", "Height", "Raise the third-person camera, or lower it below zero.", 0f, -2f..3f, step = 0.1f)
    val tilt = number("cameraTilt", "Tilt", "Angle the third-person camera down, or up below zero.", 0f, -45f..45f, step = 1f, unit = "°")
    val views = choice("cameraViews", "F5 Views", "Pick which views F5 goes through.", Views.ALL)
    val smoothing = number("cameraSmoothing", "Smoothing", "Slide the camera out when you leave first person.", 0f, 0f..100f, step = 5f)
}
