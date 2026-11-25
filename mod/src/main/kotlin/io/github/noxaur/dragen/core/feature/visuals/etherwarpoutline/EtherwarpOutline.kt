package io.github.noxaur.dragen.core.feature.visuals.etherwarpoutline

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.world.BoxStyle

class EtherwarpOutline(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "etherwarp-outline", Area.ANYWHERE, Store.GENERAL, "Outline the block Etherwarp will take you to while you sneak.")

    val style = feature.choice("etherwarpStyle", "Style", "Draw the box as edges, a tinted fill, or both.", BoxStyle.BOTH)
    val width = feature.number("etherwarpWidth", "Width", "Set how thick the box's edges are.", 2f, 1f..5f, step = 0.5f)
    val failed = feature.switch("etherwarpFailed", "Failed Warps", "Also outline blocks you can't warp to, and show why.", default = true)
    val throughWalls = feature.switch("etherwarpThroughWalls", "Through Walls", "Show the box through walls.", default = true)
    val withoutSneak = feature.switch("etherwarpWithoutSneak", "Without Sneak", "Show the box before you sneak, too.", default = false)
}
