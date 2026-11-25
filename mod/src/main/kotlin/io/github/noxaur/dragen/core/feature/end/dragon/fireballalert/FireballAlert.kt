package io.github.noxaur.dragen.core.feature.end.dragon.fireballalert

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.text.text

class FireballAlert(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "fireball-alert", Area.END, Store.END, "Warn you just before a dragon throws fireballs.")

    init {
        feature.line(Regex("""\[BOSS] .*FIREBALL!!$"""), Regex("""\[BOSS] (\w+ Dragon): .*FIREBALL!!""")) { (dragon), _ ->
            feature.alert(text { warning("Fireball") }, text { value(dragon); body(" is throwing fireballs.") })
        }
    }
}
