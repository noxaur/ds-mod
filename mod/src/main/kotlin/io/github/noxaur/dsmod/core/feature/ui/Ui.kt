package io.github.noxaur.dsmod.core.feature.ui

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.ui.activepet.ActivePet
import io.github.noxaur.dsmod.core.feature.ui.equipmentdisplay.EquipmentDisplay
import io.github.noxaur.dsmod.core.feature.ui.hud.hud
import io.github.noxaur.dsmod.core.feature.ui.inventoryhud.InventoryHud
import io.github.noxaur.dsmod.core.feature.ui.performancestats.PerformanceStats
import io.github.noxaur.dsmod.core.feature.ui.theme.theme
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.ui() = section("Interface", Role.MUTED) {
    theme()
    hud()
    group(EquipmentDisplay(c))
    group(PerformanceStats(c))
    group(ActivePet(c))
    group(InventoryHud(c))
}
