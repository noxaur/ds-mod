package io.github.noxaur.dsmod.core.feature.hud

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Moves, resizes and resets HUD blocks with the mouse while the chat is open. It reads nothing from the server, so
 * all of it is `ChatDrags` in the edge, which asks the setting on each press, and `HudDrag` in `core/hud` holds the
 * rules it shares with the HUD editor.
 */
class DragInChat(toggle: KMutableProperty0<Boolean>) : Feature("drag-in-chat", toggle, Area.ANYWHERE)
