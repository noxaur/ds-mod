package io.github.noxaur.dragen.core.feature.end.trackers

import io.github.noxaur.dragen.core.config.Groups
import io.github.noxaur.dragen.core.feature.end.trackers.bossfighttracker.BossFightTracker
import io.github.noxaur.dragen.core.feature.end.trackers.zealottracker.ZealotTracker

internal fun Groups.trackers() = group("Trackers") {
    feature(BossFightTracker(c))
    feature(ZealotTracker(c))
}
