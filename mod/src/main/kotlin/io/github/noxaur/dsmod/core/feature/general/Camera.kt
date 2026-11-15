package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Shapes the third-person view. It reads nothing from the server, so all of it is `Cameras` in the edge, with
 * the rules in `core.camera`.
 */
class Camera(toggle: KMutableProperty0<Boolean>) : Feature("camera", toggle, Area.ANYWHERE)
