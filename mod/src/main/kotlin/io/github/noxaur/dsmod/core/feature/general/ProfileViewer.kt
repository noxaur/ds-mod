package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Shows a player's profile on a screen of the mod's own, from the backend. It reads nothing from the server, so all
 * of it is `Profiles` and `ProfileScreen` in the edge, and the rules are `ProfileLookup` and `Turntable` in core.
 */
class ProfileViewer(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "profile-viewer", Area.ANYWHERE, Store.GENERAL, "Look up any player's profile with /pv and their name.")

    val clickNames = feature.switch("profileNames", "Click Names", "Click a player's name in chat to open their profile.", default = true)
}
