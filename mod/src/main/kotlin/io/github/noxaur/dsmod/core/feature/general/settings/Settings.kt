package io.github.noxaur.dsmod.core.feature.general.settings

import io.github.noxaur.dsmod.core.config.Groups

internal fun Groups.settings() = group("Settings") {
    keys(c.general.settingsKey.bind, "Settings Key", "Pick a key that opens these settings, as /dsmod does.")
    keys(c.general.favoriteKey.bind, "Favorite Key", "Pick a key that stars the setting under your mouse.", clicks = false)
}
