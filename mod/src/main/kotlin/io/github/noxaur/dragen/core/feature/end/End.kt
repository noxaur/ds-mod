package io.github.noxaur.dragen.core.feature.end

import io.github.noxaur.dragen.core.config.Sections
import io.github.noxaur.dragen.core.feature.end.dragon.dragon
import io.github.noxaur.dragen.core.feature.end.trackers.trackers
import io.github.noxaur.dragen.core.feature.end.protector.protector
import io.github.noxaur.dragen.core.text.Role

internal fun Sections.end() = section("The End", Role.EPIC) {
    dragon()
    protector()
    trackers()
}
