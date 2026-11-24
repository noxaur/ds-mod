package io.github.noxaur.dsmod.core.feature.end.drops

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.end.drops.bossfighttracker.BossFightTracker
import io.github.noxaur.dsmod.core.feature.end.drops.zealottracker.ZealotTracker

internal fun Groups.drops() = group("Drops") {
    feature(BossFightTracker(c))
    feature(ZealotTracker(c))
}
