package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Stops a right click from swapping the armor in the player's hand onto them, unless they sneak. It reads
 * nothing from the server, so all of it is `ArmorSwapGuards` in the edge.
 */
class ArmorSwapGuard(toggle: KMutableProperty0<Boolean>) : Feature("armor-swap-guard", toggle, Area.ANYWHERE)
