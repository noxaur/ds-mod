package io.github.noxaur.dragen.core.feature.inventory

import io.github.noxaur.dragen.core.config.Sections
import io.github.noxaur.dragen.core.feature.inventory.itemrarity.ItemRarity
import io.github.noxaur.dragen.core.feature.inventory.items.items
import io.github.noxaur.dragen.core.feature.inventory.protection.protection
import io.github.noxaur.dragen.core.text.Role

internal fun Sections.inventory() = section("Inventory", Role.GOOD) {
    group(ItemRarity(c))
    protection()
    items()
}
