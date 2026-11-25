package io.github.noxaur.dragen.core.feature.menus.helpers

import io.github.noxaur.dragen.core.config.Groups
import io.github.noxaur.dragen.core.feature.menus.helpers.profilecooldown.ProfileCooldown
import io.github.noxaur.dragen.core.feature.menus.helpers.storageoverview.StorageOverview

internal fun Groups.helpers() = group("Helpers") {
    feature(StorageOverview(c))
    feature(ProfileCooldown(c))
}
