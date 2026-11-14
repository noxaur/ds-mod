package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import kotlin.reflect.KMutableProperty0

/**
 * Keeps a second click from taking back the eye the player just placed. It reads nothing from the server, so
 * all of it is `EyeGuards` in the edge.
 */
class ProtectPlacedEyes(toggle: KMutableProperty0<Boolean>) : Feature("protect-placed-eyes", toggle)
