package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Adds a button beside a menu for each command the player lists, which sends it on a click. All of it is `MenuTools`
 * in the edge, with the list read by `CommandList`. This class puts it in the list that the settings, the docs check
 * and the tests go through.
 */
class CommandButtons(toggle: KMutableProperty0<Boolean>) : Feature("command-buttons", toggle, Area.ANYWHERE)
