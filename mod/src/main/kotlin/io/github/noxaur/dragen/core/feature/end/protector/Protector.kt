package io.github.noxaur.dragen.core.feature.end.protector

import io.github.noxaur.dragen.core.config.Groups
import io.github.noxaur.dragen.core.feature.end.protector.protectorspawnbeam.ProtectorSpawnBeam
import io.github.noxaur.dragen.core.feature.end.protector.protectorstatus.ProtectorStatus

internal fun Groups.protector() = group("Protector") {
    feature(ProtectorStatus(c))
    feature(ProtectorSpawnBeam(c))
}
