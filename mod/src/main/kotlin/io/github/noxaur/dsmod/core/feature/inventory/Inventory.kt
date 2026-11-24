package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.inventory.itemrarity.ItemRarity
import io.github.noxaur.dsmod.core.feature.inventory.protection.protection
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.inventory() = section("Inventory", Role.GOOD) {
    group(ItemRarity(c))
    protection()
}
