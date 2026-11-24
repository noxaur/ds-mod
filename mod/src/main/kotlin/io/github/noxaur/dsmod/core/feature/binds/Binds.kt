package io.github.noxaur.dsmod.core.feature.binds

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.binds.commandbinds.CommandBinds
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.binds() = section("Binds", Role.DIVINE) {
    group(CommandBinds(c))
}
