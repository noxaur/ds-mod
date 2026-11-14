package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.end.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.GolemStatus
import io.github.noxaur.dsmod.core.feature.end.ZealotTracker

/** Every feature the mod has. A feature missing here never runs, and `FeatureReplayTest` replays each one. */
fun features(config: DsModConfig): List<Feature> = listOf(
    DragonTimer(config.end::dragonTimer),
    GolemStatus(config.end::golemStatus),
    ZealotTracker(config.end::zealotTracker),
)
