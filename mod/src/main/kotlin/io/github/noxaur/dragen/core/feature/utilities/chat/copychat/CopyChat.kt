package io.github.noxaur.dragen.core.feature.utilities.chat.copychat

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class CopyChat(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "copy-chat", Area.ANYWHERE, Store.CHAT, "Right-click a message to copy it as text, format codes or JSON.")
}
