package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.chat.AbilitySpam
import io.github.noxaur.dsmod.core.feature.end.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.GolemStatus
import io.github.noxaur.dsmod.core.feature.end.ZealotTracker
import io.github.noxaur.dsmod.core.feature.inventory.RarityOutlines
import io.github.noxaur.dsmod.core.feature.loot.LootAlerts
import io.github.noxaur.dsmod.core.feature.slayer.SlayerTracker

/** Every feature the mod has. A feature missing here never runs, and `FeatureReplayTest` replays each one. */
fun features(config: DsModConfig): List<Feature> = listOf(
    AbilitySpam(config.chat::abilitySpam),
    DragonTimer(config.end::dragonTimer),
    GolemStatus(config.end::golemStatus),
    ZealotTracker(config.end::zealotTracker),
    RarityOutlines(config.inventory::rarityOutlines),
    LootAlerts(config.loot::lootAlerts, config.loot::lootAlertTier),
    SlayerTracker(config.slayer::slayerTracker),
)
