package io.github.noxaur.dsmod.core.feature.chat

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Copies the chat messages the player selects with the right button. It reads nothing from the server, so all
 * of it is `CopyChats` in the edge, which asks the setting on each click.
 */
class CopyChat(config: DsModConfig) :
    Feature(config, "copy-chat", Area.ANYWHERE, Store.CHAT, "Right-click messages to copy them as text, format codes or JSON.")
