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
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.feature.general.EquipmentDisplay
import io.github.noxaur.dsmod.core.feature.general.ExplosionParticleHider
import io.github.noxaur.dsmod.core.feature.general.FreeLook
import io.github.noxaur.dsmod.core.feature.general.Fullbright
import io.github.noxaur.dsmod.core.feature.inventory.ArmorSwapGuard
import io.github.noxaur.dsmod.core.feature.inventory.BackButtons
import io.github.noxaur.dsmod.core.feature.inventory.HideFiller
import io.github.noxaur.dsmod.core.feature.inventory.KeepCursor
import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
import io.github.noxaur.dsmod.core.feature.inventory.RarityMarks
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.feature.inventory.StableMenus
import io.github.noxaur.dsmod.core.feature.inventory.ThemedMenus
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
    Camera(config.general::camera),
    CommandBinds(config.general::commandBinds),
    EquipmentDisplay(
        config.general::equipmentDisplay,
        config.general::equipmentLayout,
        config.general::equipmentPieces,
        config.general::petLook,
        config.general::petPosition,
        config.general::equipmentLook,
        config.general::equipmentPlacement,
    ),
    ExplosionParticleHider(config.general::explosionParticleHider),
    FreeLook(config.general::freeLook),
    Fullbright(config.general::fullbright),
    ArmorSwapGuard(config.inventory::armorSwapGuard),
    RarityMarks(config.inventory::rarityMarks, config.inventory::markCommon),
    ThemedMenus(config.inventory::themedMenus),
    StableMenus(config.inventory::stableMenus),
    HideFiller(config.inventory::hideFiller),
    KeepCursor(config.inventory::keepCursor),
    BackButtons(config.betterUi::backButtons),
    MenuTabs(config.betterUi::menuTabs),
    SlotLock(config.inventory::slotLock),
    LootAlerts(config.loot::lootAlerts, config.loot::lootAlertTier),
    SlayerBossAlert(config.slayer::slayerBossAlert),
    SlayerTracker(config.slayer::slayerTracker),
)
