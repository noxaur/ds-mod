package io.github.noxaur.dsmod.core.feature.hud

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Moves, resizes and resets HUD blocks with the mouse while the chat is open. It reads nothing from the server, so
 * all of it is `ChatDrags` in the edge, which asks the setting on each press, and `HudDrag` in `core/hud` holds the
 * rules it shares with the HUD editor.
 */
class ChatHudEditor(config: DsModConfig) :
    Feature(config, "chat-hud-editor", Area.ANYWHERE, Store.HUD, "Drag HUD blocks with chat open, and scroll over one to resize it.", formerly = listOf("dragInChat"))
