package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.hud.HudBuilder
import io.github.noxaur.dsmod.core.text.Role

fun HudBuilder.scaleRows() {
    row("Sold") { value(1204) }
    row("Rate") { value(310); muted("/h") }
    bar("Storage", 0.8f, Role.WARNING) { warning("80%") }
}
