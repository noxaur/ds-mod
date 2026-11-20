package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area

/**
 * Puts a Go Back button in a menu the server gave none, which sends the command that opened the menu the player came
 * from. It reads the player's own commands, which no event carries, so all of it is `MenuBack` in the edge, with the
 * rule in `MenuTrail`. This class puts it in the list that the settings, the docs check and the tests go through.
 */
class BackButtons(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "back-buttons", Area.ANYWHERE, Store.BETTER_UI, "Add Go Back to menus without one, by resending your last command.")
}
