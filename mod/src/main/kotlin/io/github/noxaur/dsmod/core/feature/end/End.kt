package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.config.Sections
import io.github.noxaur.dsmod.core.feature.end.dragon.dragon
import io.github.noxaur.dsmod.core.feature.end.drops.drops
import io.github.noxaur.dsmod.core.feature.end.protector.protector
import io.github.noxaur.dsmod.core.text.Role

internal fun Sections.end() = section("The End", Role.EPIC) {
    dragon()
    protector()
    drops()
}
