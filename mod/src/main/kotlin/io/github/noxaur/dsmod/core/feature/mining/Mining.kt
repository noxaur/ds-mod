package io.github.noxaur.dsmod.core.feature.mining

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.mining.forge.forge
import io.github.noxaur.dsmod.core.feature.mining.hotdplanner.HotdPlanner
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.mining() = section("Mining", Role.WARNING) {
    group(HotdPlanner(c))
    forge()
}
