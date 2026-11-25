package io.github.noxaur.dragen.core.feature.general

import io.github.noxaur.dragen.core.config.Sections
import io.github.noxaur.dragen.core.feature.general.dragen.dragen
import io.github.noxaur.dragen.core.feature.general.mouse.mouse
import io.github.noxaur.dragen.core.text.Role

internal fun Sections.general() = section("General", Role.VALUE) {
    dragen()
    mouse()
}
