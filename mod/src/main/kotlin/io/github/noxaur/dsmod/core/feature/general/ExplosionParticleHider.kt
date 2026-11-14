package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import kotlin.reflect.KMutableProperty0

/**
 * Hides the clouds an explosion throws up, which cover the boss in a crowded fight. It reads nothing from the
 * server, so all of it is `ExplosionParticleMixin` in the edge.
 */
class ExplosionParticleHider(toggle: KMutableProperty0<Boolean>) : Feature("explosion-particle-hider", toggle)
