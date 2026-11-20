package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Keeps the cursor where it was when the server closes one menu and opens the next. It reads nothing from the
 * server, so all of it is `CursorMixin` in the edge, with the rule in `CursorMemory`. This class puts it in the
 * list that the settings, the docs check and the tests go through.
 */
class KeepCursor(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "keep-cursor", Area.ANYWHERE, Store.INVENTORY, "Leave the cursor where it was when one menu opens another.")
}
