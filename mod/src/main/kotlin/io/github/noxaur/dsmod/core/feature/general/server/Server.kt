package io.github.noxaur.dsmod.core.feature.general.server

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.general.server.dragsimonly.DragSimOnly

internal fun Groups.server() = group("Server") {
    feature(DragSimOnly(c))
}
