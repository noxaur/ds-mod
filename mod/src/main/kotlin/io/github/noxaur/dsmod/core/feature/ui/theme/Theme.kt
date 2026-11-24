package io.github.noxaur.dsmod.core.feature.ui.theme

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Theme

internal fun Groups.theme() = group("Theme") {
    pick(c.hud.theme.bind, "Theme", "Pick the colors of ds-mod's screens and controls.", Theme.entries) { Look.Swatch(it) }
}
