package io.github.noxaur.dsmod.core.feature.hud

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.hud.activepet.ActivePet
import io.github.noxaur.dsmod.core.feature.hud.equipmentdisplay.EquipmentDisplay
import io.github.noxaur.dsmod.core.feature.hud.inventoryhud.InventoryHud
import io.github.noxaur.dsmod.core.feature.hud.layout.layout
import io.github.noxaur.dsmod.core.feature.hud.performancestats.PerformanceStats
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.hud() = section("HUD", Role.MUTED) {
    layout()
    group(EquipmentDisplay(c))
    group(ActivePet(c))
    group(InventoryHud(c))
    group(PerformanceStats(c))
}
