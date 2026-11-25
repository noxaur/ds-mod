package io.github.noxaur.dragen.core.feature.utilities.chat.profileviewer

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class ProfileViewer(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "profile-viewer", Area.ANYWHERE, Store.GENERAL, "Look up any player's profile with /pv and their name.")

    val clickNames = feature.switch("profileNames", "Click Names", "Click a player's name in chat to open their profile.", default = true)
}
