package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Resizes, tilts, spins and poses player models, as only this player sees them. It reads nothing from the server, so
 * all of it is `PlayerModels` in the edge, with the rules in `core.player`.
 */
class PlayerModel(toggle: KMutableProperty0<Boolean>) : Feature("player-model", toggle, Area.ANYWHERE)
