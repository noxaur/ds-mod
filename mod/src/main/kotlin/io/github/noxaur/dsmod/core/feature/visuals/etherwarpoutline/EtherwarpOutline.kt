package io.github.noxaur.dsmod.core.feature.visuals.etherwarpoutline

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.world.BoxStyle

class EtherwarpOutline(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "etherwarp-outline", Area.ANYWHERE, Store.GENERAL, "Box the block Etherwarp will take you to while you sneak.")

    val style = feature.choice("etherwarpStyle", "Style", "Draw the box as edges, a tinted fill, or both.", BoxStyle.BOTH)
    val width = feature.number("etherwarpWidth", "Width", "Set how thick the box's edges are.", 2f, 1f..5f, step = 0.5f)
    val failed = feature.switch("etherwarpFailed", "Failed Warps", "Box a block you can't warp to as well, and say why.", default = true)
    val throughWalls = feature.switch("etherwarpThroughWalls", "Through Walls", "Show the whole box, even behind the blocks in front of it.", default = true)
    val withoutSneak = feature.switch("etherwarpWithoutSneak", "Without Sneak", "Show the box before you sneak, too.", default = false)
}
