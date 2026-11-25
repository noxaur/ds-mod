package io.github.noxaur.dragen.core.feature.mining

import io.github.noxaur.dragen.core.config.Sections
import io.github.noxaur.dragen.core.feature.mining.forge.forge
import io.github.noxaur.dragen.core.feature.mining.hotdplanner.HotdPlanner
import io.github.noxaur.dragen.core.text.Role

internal fun Sections.mining() = section("Mining", Role.WARNING) {
    group(HotdPlanner(c))
    forge()
}
