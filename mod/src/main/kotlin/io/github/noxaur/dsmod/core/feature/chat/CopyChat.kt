package io.github.noxaur.dsmod.core.feature.chat

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class CopyChat(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "copy-chat", Area.ANYWHERE, Store.CHAT, "Right-click messages to copy them as text, format codes or JSON.")
}
