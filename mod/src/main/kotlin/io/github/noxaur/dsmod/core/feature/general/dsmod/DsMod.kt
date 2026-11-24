package io.github.noxaur.dsmod.core.feature.general.dsmod

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.general.dsmod.dragsimonly.DragSimOnly
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Theme

internal fun Groups.dsMod() = group("ds-mod") {
    keys(c.general.settingsKey.bind, "Settings Key", "Pick a key that opens these settings, as /dsmod does.")
    keys(c.general.favoriteKey.bind, "Favorite Key", "Pick a key that stars the setting under your mouse.", clicks = false)
    pick(c.hud.theme.bind, "Theme", "Pick the colors of ds-mod's screens and controls.", Theme.entries) { Look.Swatch(it) }
    feature(DragSimOnly(c))
}
