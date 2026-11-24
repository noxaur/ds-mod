package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.general.dsmod.dsMod
import io.github.noxaur.dsmod.core.feature.general.mouse.mouse
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.general() = section("General", Role.VALUE) {
    dsMod()
    mouse()
}
