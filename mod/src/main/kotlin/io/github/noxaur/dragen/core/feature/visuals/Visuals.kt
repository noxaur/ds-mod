package io.github.noxaur.dragen.core.feature.visuals

import io.github.noxaur.dragen.core.config.Sections
import io.github.noxaur.dragen.core.feature.visuals.animations.Animations
import io.github.noxaur.dragen.core.feature.visuals.camera.camera
import io.github.noxaur.dragen.core.feature.visuals.etherwarpoutline.EtherwarpOutline
import io.github.noxaur.dragen.core.feature.visuals.playermodel.PlayerModel
import io.github.noxaur.dragen.core.feature.visuals.world.world
import io.github.noxaur.dragen.core.text.Role

internal fun Sections.visuals() = section("Visuals", Role.ACTION) {
    group(Animations(c))
    group(PlayerModel(c))
    camera()
    world()
    group(EtherwarpOutline(c), "Etherwarp")
}
