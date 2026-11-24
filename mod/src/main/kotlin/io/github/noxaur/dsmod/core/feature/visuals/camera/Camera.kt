package io.github.noxaur.dsmod.core.feature.visuals.camera

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.visuals.camera.freelook.FreeLook
import io.github.noxaur.dsmod.core.feature.visuals.camera.thirdperson.Camera

internal fun Groups.camera() = group("Camera") {
    feature(Camera(c))
    feature(FreeLook(c))
}
