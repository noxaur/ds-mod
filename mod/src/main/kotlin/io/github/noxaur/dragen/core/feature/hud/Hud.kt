package io.github.noxaur.dragen.core.feature.hud

import io.github.noxaur.dragen.core.config.Sections
import io.github.noxaur.dragen.core.feature.hud.equipmentdisplay.EquipmentDisplay
import io.github.noxaur.dragen.core.feature.hud.layout.layout
import io.github.noxaur.dragen.core.feature.hud.performancestats.PerformanceStats
import io.github.noxaur.dragen.core.text.Role

internal fun Sections.hud() = section("HUD", Role.MUTED) {
    layout()
    group(EquipmentDisplay(c))
    group(PerformanceStats(c))
}
