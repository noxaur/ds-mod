package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Hides the clouds an explosion throws up, which cover the boss in a crowded fight. It reads nothing from the
 * server, so all of it is `ExplosionParticleMixin` in the edge.
 */
class HideExplosions(toggle: KMutableProperty0<Boolean>) : Feature("hide-explosions", toggle, Area.ANYWHERE)
