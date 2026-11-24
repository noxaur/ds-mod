package io.github.noxaur.dsmod.core.feature.hud.layout.chathudeditor

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class ChatHudEditor(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "chat-hud-editor", Area.ANYWHERE, Store.HUD, "Drag HUD blocks with chat open, and scroll over one to resize it.", formerly = listOf("dragInChat"))
}
