package io.github.noxaur.dsmod.core.feature.menus.keepcursor

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

class KeepCursor(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "keep-cursor", Area.ANYWHERE, Store.INVENTORY, "Keep the cursor where it is when one menu opens another.")
}
