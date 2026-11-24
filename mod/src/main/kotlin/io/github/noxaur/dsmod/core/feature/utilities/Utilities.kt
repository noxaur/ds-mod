package io.github.noxaur.dsmod.core.feature.utilities

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.utilities.autoclick.AutoClick
import io.github.noxaur.dsmod.core.feature.utilities.chat.chat
import io.github.noxaur.dsmod.core.feature.utilities.commandbinds.CommandBinds
import io.github.noxaur.dsmod.core.feature.utilities.waypoints.Waypoints
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.utilities() = section("Utilities", Role.DIVINE) {
    group(CommandBinds(c))
    group(Waypoints(c))
    group(AutoClick(c))
    chat()
}
