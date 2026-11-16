package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import kotlin.reflect.KMutableProperty0

/**
 * Boxes the block an Etherwarp would land on. The box follows the crosshair, so it is found and drawn on each frame
 * by `Etherwarps` in the edge, from the rules in `core/hand/Etherwarp`. This class puts it in the list that the
 * settings, the docs check and the tests go through.
 */
class EtherwarpOutline(toggle: KMutableProperty0<Boolean>) : Feature("etherwarp-outline", toggle, Area.ANYWHERE)
