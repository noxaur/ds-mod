package io.github.noxaur.dsmod.core.feature.chat

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Copies the chat messages the player selects with the right button. It reads nothing from the server, so all
 * of it is `CopyChats` in the edge, which asks the setting on each click.
 */
class CopyChat(toggle: KMutableProperty0<Boolean>) : Feature("copy-chat", toggle, Area.ANYWHERE)
