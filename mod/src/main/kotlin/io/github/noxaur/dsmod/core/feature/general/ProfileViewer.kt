package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Shows a player's profile on a screen of the mod's own, from the backend. It reads nothing from the server, so all
 * of it is `Profiles` and `ProfileScreen` in the edge, and the rules are `ProfileLookup` and `Turntable` in core.
 */
class ProfileViewer(toggle: KMutableProperty0<Boolean>) : Feature("profile-viewer", toggle, Area.ANYWHERE)
