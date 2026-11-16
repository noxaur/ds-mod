package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.chat.HideCooldownMessages
import io.github.noxaur.dsmod.core.feature.chat.CopyChat
import io.github.noxaur.dsmod.core.feature.end.ProtectorSpawnBeam
import io.github.noxaur.dsmod.core.feature.end.BossFightTracker
import io.github.noxaur.dsmod.core.feature.end.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.FireballAlert
import io.github.noxaur.dsmod.core.feature.end.ProtectorStatus
import io.github.noxaur.dsmod.core.feature.end.EyeGuard
import io.github.noxaur.dsmod.core.feature.end.ZealotTracker
import io.github.noxaur.dsmod.core.feature.general.AbilityCooldowns
import io.github.noxaur.dsmod.core.feature.general.ActivePet
import io.github.noxaur.dsmod.core.feature.general.Animations
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.feature.general.DamageNumbers
import io.github.noxaur.dsmod.core.feature.general.EquipmentDisplay
import io.github.noxaur.dsmod.core.feature.general.EtherwarpOutline
import io.github.noxaur.dsmod.core.feature.general.HideExplosions
import io.github.noxaur.dsmod.core.feature.general.FreeLook
import io.github.noxaur.dsmod.core.feature.general.InventoryHud
import io.github.noxaur.dsmod.core.feature.general.Fullbright
import io.github.noxaur.dsmod.core.feature.general.ProfileViewer
import io.github.noxaur.dsmod.core.feature.hud.ChatHudEditor
import io.github.noxaur.dsmod.core.feature.inventory.ArmorSwapGuard
import io.github.noxaur.dsmod.core.feature.inventory.BackButtons
import io.github.noxaur.dsmod.core.feature.inventory.HideGlassPanes
import io.github.noxaur.dsmod.core.feature.inventory.KeepCursor
import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.feature.inventory.FixedMenuLayout
import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.core.feature.loot.RareDropAlert
import io.github.noxaur.dsmod.core.feature.slayer.SlayerBossAlert
import io.github.noxaur.dsmod.core.feature.slayer.SlayerTracker

/** Every feature the mod has. `FeaturesListedTest` fails on a feature missing here, and `FeatureReplayTest` replays each one. */
fun features(config: DsModConfig): List<Feature> = listOf(
    HideCooldownMessages(config.chat::hideCooldownMessages),
    CopyChat(config.chat::copyChat),
    ProtectorSpawnBeam(config.end::protectorSpawnBeam, config.end::beamSections, config.end::beamInFight),
    BossFightTracker(config.end::bossFightTracker),
    DragonTimer(config.end::dragonTimer),
    FireballAlert(config.end::fireballAlert),
    ProtectorStatus(config.end::protectorStatus),
    EyeGuard(config.end::eyeGuard),
    ZealotTracker(config.end::zealotTracker),
    AbilityCooldowns(
        config.general::abilityCooldowns,
        config.general::cooldownList,
        config.general::cooldownOnItems,
        config.general::cooldownShortest,
        config.general::cooldownReadyAlert,
        config.general::cooldownAlertFrom,
    ),
    Animations(config.general::animations),
    Camera(config.general::camera),
    CommandBinds(config.general::commandBinds),
    DamageNumbers(config.general::damageNumbers, config.general::damageShown, config.general::damageShort, config.general::damageDecimals),
    EquipmentDisplay(
        config.general::equipmentDisplay,
        config.general::equipmentLayout,
        config.general::equipmentPieces,
        config.general::petLook,
        config.general::petPosition,
        config.general::equipmentLook,
        config.general::equipmentPlacement,
    ),
    ActivePet(
        config.general::activePet,
        config.general::activePetIcon,
        config.general::activePetItem,
        config.general::activePetXp,
        config.general::activePetRate,
        config.general::activePetNext,
        config.general::markActivePet,
        config.general::hideAutopet,
    ),
    EtherwarpOutline(config.general::etherwarpOutline),
    HideExplosions(config.general::hideExplosions),
    InventoryHud(
        config.general::inventoryHud,
        config.general::inventoryRows,
        config.general::inventoryLook,
        config.general::inventoryPlacement,
        config.general::inventoryLayout,
        config.general::inventoryOpacity,
        config.general::inventoryPickup,
    ),
    FreeLook(config.general::freeLook),
    Fullbright(config.general::fullbright),
    ProfileViewer(config.general::profileViewer),
    ChatHudEditor(config.hud::chatHudEditor),
    ArmorSwapGuard(config.inventory::armorSwapGuard),
    ItemRarity(config.inventory::itemRarity, config.inventory::markCommon),
    MenuTheme(config.inventory::menuTheme),
    FixedMenuLayout(config.inventory::fixedMenuLayout),
    HideGlassPanes(config.inventory::hideGlassPanes),
    KeepCursor(config.inventory::keepCursor),
    SlotLock(config.inventory::slotLock),
    BackButtons(config.betterUi::backButtons),
    MenuTabs(config.betterUi::menuTabs),
    RareDropAlert(config.loot::rareDropAlert, config.loot::lootAlertTier),
    SlayerBossAlert(config.slayer::slayerBossAlert),
    SlayerTracker(config.slayer::slayerTracker),
)
