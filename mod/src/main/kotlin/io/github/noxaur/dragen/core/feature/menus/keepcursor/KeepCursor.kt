package io.github.noxaur.dragen.core.feature.menus.keepcursor

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area

class KeepCursor(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "keep-cursor", Area.ANYWHERE, Store.INVENTORY, "Keep the cursor where it is when one menu opens another.")
}
