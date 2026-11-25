package io.github.noxaur.dragen.core.feature.general.dragen

import io.github.noxaur.dragen.core.config.Groups
import io.github.noxaur.dragen.core.feature.general.dragen.dragsimonly.DragSimOnly
import io.github.noxaur.dragen.core.ui.Look
import io.github.noxaur.dragen.core.ui.Theme

internal fun Groups.dragen() = group("Dragen") {
    keys(c.general.settingsKey.bind, "Settings Key", "Pick a key that opens these settings, as /dragen does.")
    keys(c.general.favoriteKey.bind, "Favorite Key", "Pick a key that stars the setting under your mouse.", clicks = false)
    pick(c.hud.theme.bind, "Theme", "Pick the colors of Dragen's screens and controls.", Theme.entries) { Look.Swatch(it) }
    feature(DragSimOnly(c))
}
