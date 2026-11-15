package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Looks around in third person on a key without turning the player. It reads nothing from the server and sends
 * nothing, so all of it is `Cameras` in the edge, with the rules in `core.camera`.
 */
class FreeLook(toggle: KMutableProperty0<Boolean>) : Feature("free-look", toggle, Area.ANYWHERE)
