package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Sends a command the player picked when they press its key in the world. It reads nothing from the server, so
 * all of it is `CommandKeys` in the edge, with the binds in the settings and their rules in `core.input`.
 */
class CommandBinds(toggle: KMutableProperty0<Boolean>) : Feature("command-binds", toggle, Area.ANYWHERE)
