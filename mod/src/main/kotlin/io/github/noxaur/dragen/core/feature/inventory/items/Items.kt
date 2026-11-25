package io.github.noxaur.dragen.core.feature.inventory.items

import io.github.noxaur.dragen.core.config.Groups
import io.github.noxaur.dragen.core.feature.inventory.items.itemoverview.ItemOverview
import io.github.noxaur.dragen.core.feature.inventory.items.raredropalert.RareDropAlert

internal fun Groups.items() = group("Items") {
    feature(ItemOverview(c))
    feature(RareDropAlert(c))
}
