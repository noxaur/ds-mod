package io.github.noxaur.dsmod.core.feature.camera

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.camera.freelook.FreeLook
import io.github.noxaur.dsmod.core.feature.camera.thirdperson.Camera
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.camera() = section("Camera", Role.ACTION) {
    group(Camera(c), "Third Person")
    group(FreeLook(c))
}
