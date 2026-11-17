package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.world.BoxStyle

/**
 * Boxes the block an Etherwarp would land on. The box follows the crosshair, so it is found and drawn on each frame
 * by `Etherwarps` in the edge, from the rules in `core/hand/Etherwarp`. This class declares its settings and puts it
 * in the settings, the docs check and the tests.
 */
class EtherwarpOutline(config: DsModConfig) :
    Feature(config, "etherwarp-outline", Area.ANYWHERE, Store.GENERAL, "Box the block Etherwarp will take you to while you sneak.") {
    val style = choice("etherwarpStyle", "Style", "Draw the box as edges, a tinted fill, or both.", BoxStyle.BOTH)
    val width = number("etherwarpWidth", "Width", "Set how thick the box's edges are.", 2f, 1f..5f, step = 0.5f)
    val failed = switch("etherwarpFailed", "Failed Warps", "Box a block you can't warp to as well, and say why.", default = true)
    val throughWalls = switch("etherwarpThroughWalls", "Through Walls", "Show the whole box, even behind the blocks in front of it.", default = true)
    val withoutSneak = switch("etherwarpWithoutSneak", "Without Sneak", "Show the box before you sneak, too.", default = false)
}
