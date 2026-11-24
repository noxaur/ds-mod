package io.github.noxaur.dsmod.core.feature.general.profileviewer

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class ProfileViewer(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "profile-viewer", Area.ANYWHERE, Store.GENERAL, "Look up any player's profile with /pv and their name.")

    val clickNames = feature.switch("profileNames", "Click Names", "Click a player's name in chat to open their profile.", default = true)
}
