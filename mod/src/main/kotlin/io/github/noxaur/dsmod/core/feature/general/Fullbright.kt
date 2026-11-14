package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import kotlin.reflect.KMutableProperty0

/**
 * Lights the whole world. It reads nothing from the server, so all of it is `FullbrightMixin` in the edge,
 * which asks the setting each frame. This class puts it in the list that the settings, the docs check and the
 * tests go through.
 */
class Fullbright(toggle: KMutableProperty0<Boolean>) : Feature("fullbright", toggle)
