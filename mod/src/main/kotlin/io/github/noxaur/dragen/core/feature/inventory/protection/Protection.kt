package io.github.noxaur.dragen.core.feature.inventory.protection

import io.github.noxaur.dragen.core.config.Groups
import io.github.noxaur.dragen.core.feature.inventory.protection.armorswapguard.ArmorSwapGuard
import io.github.noxaur.dragen.core.feature.inventory.protection.slotlock.SlotLock

internal fun Groups.protection() = group("Protection") {
    feature(SlotLock(c))
    feature(ArmorSwapGuard(c))
}
