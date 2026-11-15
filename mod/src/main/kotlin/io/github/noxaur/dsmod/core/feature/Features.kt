package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.chat.AbilitySpam
import io.github.noxaur.dsmod.core.feature.chat.CopyChat
import io.github.noxaur.dsmod.core.feature.end.BossBeacon
import io.github.noxaur.dsmod.core.feature.end.BossDrops
import io.github.noxaur.dsmod.core.feature.end.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.FireballAlert
import io.github.noxaur.dsmod.core.feature.end.GolemStatus
import io.github.noxaur.dsmod.core.feature.end.ProtectPlacedEyes
import io.github.noxaur.dsmod.core.feature.end.ZealotTracker
import io.github.noxaur.dsmod.core.feature.general.ActivePet
import io.github.noxaur.dsmod.core.feature.general.ArmorDisplay
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.feature.general.ExplosionParticleHider
import io.github.noxaur.dsmod.core.feature.general.FreeLook
import io.github.noxaur.dsmod.core.feature.general.Fullbright
import io.github.noxaur.dsmod.core.feature.inventory.ArmorSwapGuard
import io.github.noxaur.dsmod.core.feature.inventory.RarityMarks
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.feature.loot.LootAlerts
import io.github.noxaur.dsmod.core.feature.slayer.SlayerBossAlert
import io.github.noxaur.dsmod.core.feature.slayer.SlayerTracker

/** Every feature the mod has. A feature missing here never runs, and `FeatureReplayTest` replays each one. */
fun features(config: DsModConfig): List<Feature> = listOf(
    AbilitySpam(config.chat::abilitySpam),
    CopyChat(config.chat::copyChat),
    BossBeacon(config.end::bossBeacon, config.end::beamSections, config.end::beamInFight),
    BossDrops(config.end::bossDrops),
    DragonTimer(config.end::dragonTimer),
    FireballAlert(config.end::fireballAlert),
    GolemStatus(config.end::golemStatus),
    ProtectPlacedEyes(config.end::protectPlacedEyes),
    ZealotTracker(config.end::zealotTracker),
    ActivePet(config.general::activePet),
    ArmorDisplay(config.general::armorDisplay, config.general::armorLayout, config.general::armorPieces),
    Camera(config.general::camera),
    CommandBinds(config.general::commandBinds),
    ExplosionParticleHider(config.general::explosionParticleHider),
    FreeLook(config.general::freeLook),
    Fullbright(config.general::fullbright),
    ArmorSwapGuard(config.inventory::armorSwapGuard),
    RarityMarks(config.inventory::rarityMarks, config.inventory::markCommon),
    SlotLock(config.inventory::slotLock),
    LootAlerts(config.loot::lootAlerts, config.loot::lootAlertTier),
    SlayerBossAlert(config.slayer::slayerBossAlert),
    SlayerTracker(config.slayer::slayerTracker),
)
