package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Keeps the cursor where it was when the server closes one menu and opens the next. It reads nothing from the
 * server, so all of it is `CursorMixin` in the edge, with the rule in `CursorMemory`. This class puts it in the
 * list that the settings, the docs check and the tests go through.
 */
class KeepCursor(toggle: KMutableProperty0<Boolean>) : Feature("keep-cursor", toggle, Area.ANYWHERE)
