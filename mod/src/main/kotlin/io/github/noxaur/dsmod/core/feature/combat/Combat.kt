package io.github.noxaur.dsmod.core.feature.combat

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.combat.bosses.bosses
import io.github.noxaur.dsmod.core.feature.combat.damagenumbers.DamageNumbers
import io.github.noxaur.dsmod.core.feature.combat.loot.loot
import io.github.noxaur.dsmod.core.feature.combat.slayers.slayers
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.combat() = section("Combat", Role.BAD) {
    group(DamageNumbers(c))
    slayers()
    bosses()
    loot()
}
