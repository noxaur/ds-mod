package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.chat.AbilitySpam
import io.github.noxaur.dsmod.core.feature.end.BossBeacon
import io.github.noxaur.dsmod.core.feature.end.BossDrops
import io.github.noxaur.dsmod.core.feature.end.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.GolemStatus
import io.github.noxaur.dsmod.core.feature.end.ProtectPlacedEyes
import io.github.noxaur.dsmod.core.feature.end.ZealotTracker
import io.github.noxaur.dsmod.core.feature.general.Alerts
import io.github.noxaur.dsmod.core.feature.general.ExplosionParticleHider
import io.github.noxaur.dsmod.core.feature.general.Fullbright
import io.github.noxaur.dsmod.core.feature.inventory.ArmorSwapGuard
import io.github.noxaur.dsmod.core.feature.inventory.RarityOutlines
import io.github.noxaur.dsmod.core.feature.loot.LootAlerts
import io.github.noxaur.dsmod.core.feature.slayer.SlayerTracker

/** Every feature the mod has. A feature missing here never runs, and `FeatureReplayTest` replays each one. */
fun features(config: DsModConfig): List<Feature> = listOf(
    AbilitySpam(config.chat::abilitySpam),
    BossBeacon(config.end::bossBeacon, config.end::beamSections, config.end::beamInFight),
    BossDrops(config.end::bossDrops),
    DragonTimer(config.end::dragonTimer),
    GolemStatus(config.end::golemStatus),
    ProtectPlacedEyes(config.end::protectPlacedEyes),
    ZealotTracker(config.end::zealotTracker),
    Alerts(config.general::alerts, config.general::fireballAlert, config.general::slayerBossAlert),
    ExplosionParticleHider(config.general::explosionParticleHider),
    Fullbright(config.general::fullbright),
    ArmorSwapGuard(config.inventory::armorSwapGuard),
    RarityOutlines(config.inventory::rarityOutlines),
    LootAlerts(config.loot::lootAlerts, config.loot::lootAlertTier),
    SlayerTracker(config.slayer::slayerTracker),
)
