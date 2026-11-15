package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Shapes how the held item swings and where it sits in first person. It reads nothing from the server, so all of it
 * is `HeldItems` in the edge, with the rules in `core.hand`.
 */
class Animations(toggle: KMutableProperty0<Boolean>) : Feature("animations", toggle, Area.ANYWHERE)
