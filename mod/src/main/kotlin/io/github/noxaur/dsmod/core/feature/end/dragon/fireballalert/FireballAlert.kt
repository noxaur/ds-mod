package io.github.noxaur.dsmod.core.feature.end.dragon.fireballalert

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.text.text

class FireballAlert(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "fireball-alert", Area.END, Store.END, "Warn you just before a dragon throws fireballs.")

    init {
        feature.line(Regex("""\[BOSS] .*FIREBALL!!$"""), Regex("""\[BOSS] (\w+ Dragon): .*FIREBALL!!""")) { (dragon), _ ->
            feature.alert(text { warning("Fireball") }, text { value(dragon); body(" is throwing fireballs.") })
        }
    }
}
