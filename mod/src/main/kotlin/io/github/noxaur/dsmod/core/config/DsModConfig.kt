@file:OptIn(ExperimentalSerializationApi::class)

package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.camera.ThirdPerson
import io.github.noxaur.dsmod.core.camera.Views
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.general.DamageShown
import io.github.noxaur.dsmod.core.feature.general.EquipmentLayout
import io.github.noxaur.dsmod.core.feature.general.EquipmentLook
import io.github.noxaur.dsmod.core.feature.general.EquipmentPiece
import io.github.noxaur.dsmod.core.feature.general.InventoryLayout
import io.github.noxaur.dsmod.core.feature.general.InventoryLook
import io.github.noxaur.dsmod.core.feature.general.InventoryRow
import io.github.noxaur.dsmod.core.feature.general.PetLook
import io.github.noxaur.dsmod.core.feature.general.PetPosition
import io.github.noxaur.dsmod.core.hand.SwingStyle
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.player.ModelPose
import io.github.noxaur.dsmod.core.player.ModelTarget
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.core.world.BoxStyle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Every saved setting, as plain data. `SettingsTree` names each field, describes it and gives it a row, and
 * `ConfigFile` writes it to `config/dsmod/config.json` by the field names, so a field's name is its key in the file:
 * a renamed field keeps its old name in `@JsonNames`, or players lose what they set. A field's initializer is its
 * default. The mixins read the fields from Java, so each has `@JvmField`.
 */
@Serializable
class DsModConfig {
    /** The ids of the rows the player starred, which the settings' Favorites lists. See `SettingsTree.favorites`. */
    @JvmField
    var favorites: List<String> = emptyList()

    @JvmField
    val general = General()

    @JvmField
    val hud = Hud()

    @JvmField
    val chat = Chat()

    @JvmField
    val inventory = Inventory()

    @JvmField
    val betterUi = BetterUi()

    @JvmField
    val loot = Loot()

    @JvmField
    val end = End()

    @JvmField
    val slayer = Slayer()

    @JvmField
    val contribute = Contribute()

    @Serializable
    class Chat {
        @JsonNames("abilitySpam")
        @JvmField
        var hideCooldownMessages = true

        @JvmField
        var copyChat = true
    }

    @Serializable
    class End {
        @JvmField
        var zealotTracker = true

        @JsonNames("protectPlacedEyes")

        @JvmField
        var eyeGuard = true

        @JvmField
        var dragonTimer = true

        @JvmField
        var fireballAlert = true
    

        @JsonNames("golemStatus")
    

        @JvmField
        var protectorStatus = true

        @JsonNames("bossBeacon")

        @JvmField
        var protectorSpawnBeam = true

        /** The parts of the Protector's cycle that show the beam, in zealots killed since the last defeat, out of 200. */
        var beamSections: List<Section> = listOf(Section(175, 200))

        var beamInFight = false

        @JsonNames("bossDrops")

        @JvmField
        var bossFightTracker = true
    }

    @Serializable
    class General {
        @JvmField
        var onlyOnDragSim = true

        /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as `KeyBind` holds it. */
        @JvmField
        var settingsKey = NO_KEY

        /** A key's GLFW code or a mouse button, as for [settingsKey]. */
        @JvmField
        var favoriteKey = KEY_F

        @JvmField
        var fullbright = false

        @JsonNames("explosionParticleHider")

        @JvmField
        var hideExplosions = false

        @JvmField
        var damageNumbers = false

        @JvmField
        var damageShown = DamageShown.ALL

        @JvmField
        var damageShort = true

        @JvmField
        var damageDecimals = 1f

        @JvmField
        var animations = true

        @JvmField
        var swingStyle = SwingStyle.VANILLA

        @JvmField
        var swingSpeed = 1f

        @JvmField
        var itemSize = 1f

        @JvmField
        var itemX = 0f

        @JvmField
        var itemY = 0f

        @JvmField
        var itemZ = 0f

        @JvmField
        var itemYaw = 0f

        @JvmField
        var itemPitch = 0f

        @JvmField
        var itemRoll = 0f

        @JvmField
        var scaleSwing = true

        @JvmField
        var noSwing = false

        @JvmField
        var noBowSwing = false

        @JvmField
        var ignoreHaste = false

        @JvmField
        var noEquipReset = false

        /** Copies each Animations setting but the switch from a fresh config, so the defaults are declared once. */
        fun resetAnimations() {
            val fresh = General()
            swingStyle = fresh.swingStyle
            swingSpeed = fresh.swingSpeed
            itemSize = fresh.itemSize
            itemX = fresh.itemX
            itemY = fresh.itemY
            itemZ = fresh.itemZ
            itemYaw = fresh.itemYaw
            itemPitch = fresh.itemPitch
            itemRoll = fresh.itemRoll
            scaleSwing = fresh.scaleSwing
            noSwing = fresh.noSwing
            noBowSwing = fresh.noBowSwing
            ignoreHaste = fresh.ignoreHaste
            noEquipReset = fresh.noEquipReset
        }

        @JvmField
        var playerModel = false

        @JvmField
        var modelTarget = ModelTarget.SELF

        @JvmField
        var modelSize = 1f

        @JvmField
        var modelWidth = 1f

        @JvmField
        var modelHeight = 1f

        @JvmField
        var modelDepth = 1f

        @JvmField
        var modelTiltForward = 0f

        @JvmField
        var modelTiltSideways = 0f

        @JvmField
        var modelUpsideDown = false

        /** Turns a second, the other way below zero. */
        @JvmField
        var modelSpin = 0f

        @JvmField
        var modelPose = ModelPose.GAME

        /** Copies each Player Model setting but the switch and whose model from a fresh config. */
        fun resetPlayerModel() {
            val fresh = General()
            modelSize = fresh.modelSize
            modelWidth = fresh.modelWidth
            modelHeight = fresh.modelHeight
            modelDepth = fresh.modelDepth
            modelTiltForward = fresh.modelTiltForward
            modelTiltSideways = fresh.modelTiltSideways
            modelUpsideDown = fresh.modelUpsideDown
            modelSpin = fresh.modelSpin
            modelPose = fresh.modelPose
        }

        @JvmField
        var abilityCooldowns = true

        @JvmField
        var cooldownList = true

        @JvmField
        var cooldownOnItems = true

        @JvmField
        var cooldownShortest = 2f

        @JvmField
        var cooldownReadyAlert = false

        @JvmField
        var cooldownAlertFrom = 20f

        @JvmField
        var equipmentDisplay = true

        @JvmField
        var equipmentPieces: MutableList<EquipmentPiece> = EquipmentPiece.entries.toMutableList()

        @JvmField
        var equipmentLayout = EquipmentLayout.ROW

        @JvmField
        var petLook = PetLook.ICON

        @JvmField
        var petPosition = PetPosition.WITH_ARMOR

        @JvmField
        var equipmentLook = EquipmentLook.PLATE

        @JvmField
        var equipmentPlacement = HudDock.FREE

        @JsonNames("inventoryPreview")

        @JvmField
        var inventoryHud = false

        @JvmField
        var inventoryRows: MutableList<InventoryRow> = mutableListOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM)

        @JvmField
        var inventoryLook = InventoryLook.INVENTORY

        @JvmField
        var inventoryPlacement = HudDock.FREE

        @JvmField
        var inventoryKey = NO_KEY

        @JvmField
        var inventoryKeyMode = KeyMode.TOGGLE

        @JvmField
        var inventoryLayout = InventoryLayout.ROWS

        @JvmField
        var inventoryOpacity = 100f

        @JvmField
        var inventoryPickup = false

        @JvmField
        var etherwarpOutline = true

        @JvmField
        var etherwarpStyle = BoxStyle.BOTH

        @JvmField
        var etherwarpWidth = 2f

        @JvmField
        var etherwarpFailed = true

        @JvmField
        var etherwarpThroughWalls = true

        @JvmField
        var etherwarpWithoutSneak = false

        @JvmField
        var camera = true

        @JvmField
        var cameraDistance = ThirdPerson.DISTANCE

        @JvmField
        var cameraHeight = 0f

        @JvmField
        var cameraTilt = 0f

        @JvmField
        var cameraViews = Views.ALL

        @JvmField
        var cameraSmoothing = 0f

        @JvmField
        var freeLook = true

        /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as `KeyBind` holds it. */
        @JvmField
        var freeLookKey = LEFT_ALT

        @JvmField
        var freeLookMode = KeyMode.HOLD

        @JvmField
        var commandBinds = true

        /** Every bind, in the order the player put them, each naming its group. */
        var binds: List<CommandBind> = emptyList()

        /** The groups after the first, which is unnamed, in the order the player made them. */
        var bindGroups: List<String> = emptyList()

        /** The groups whose binds are folded away in the list. */
        var collapsedGroups: List<String> = emptyList()

        /** The groups switched off, whose binds send nothing whatever their own switch says. */
        var offGroups: List<String> = emptyList()

        @JvmField
        var sequenceGap = 250f

        @JvmField
        var profileViewer = true

        @JvmField
        var profileNames = true

        private companion object {
            /** GLFW's code for the left Alt key. */
            const val LEFT_ALT = 342

            /** GLFW's code for F, NotEnoughUpdates' key for a favorite. */
            const val KEY_F = 70
        }
    }

    @Serializable
    class Inventory {
        @JsonNames("rarityMarks")
        @JvmField
        var itemRarity = true

        @JvmField
        var rarityPreset = RarityPreset.GILDED

        /** The player's own style for every container, their inventory included, shown while [rarityPreset] is Custom. */
        @JvmField
        var inventoryStyle = SlotStyle()

        @JvmField
        var hotbarStyle = SlotStyle()

        /** While true the hotbar is drawn in [inventoryStyle], and [hotbarStyle] waits for the player to unlink them. */
        @JvmField
        var hotbarMatches = true

        @JvmField
        var hotbarMarks = true

        @JvmField
        var markCommon = false

        /** The style containers show: the preset's, or the player's own under Custom. */
        fun containerStyle(): SlotStyle = rarityPreset.style ?: inventoryStyle

        /** The look the hotbar's marks are drawn in: the inventory's while they match, or the hotbar's own. */
        fun hotbarLook(): SlotStyle = if (hotbarMatches) containerStyle() else hotbarStyle

        @JsonNames("themedMenus")

        @JvmField
        var menuTheme = true

        @JvmField
        var menuLook = MenuLook.SOLID

        @JvmField
        var glassOpacity = 60f

        @JsonNames("stableMenus")

        @JvmField
        var fixedMenuLayout = true

        @JsonNames("hideFiller")

        @JvmField
        var hideGlassPanes = true

        @JvmField
        var keepCursor = true

        @JvmField
        var slotLock = true

        /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as `KeyBind` holds it. */
        @JvmField
        var slotLockKey = KEY_L

        @JvmField
        var itemLockKey = KEY_K

        @JvmField
        var armorSwapGuard = false

        private companion object {
            /** GLFW's codes for the K and L keys. */
            const val KEY_K = 75
            const val KEY_L = 76
        }
    }

    @Serializable
    class BetterUi {
        @JvmField
        var enabled = true

        @JvmField
        var backButtons = true

        @JvmField
        var menuTabs = true

        /** The tabs the player set up, in the editor. */
        @JvmField
        var tabs: List<MenuTab> = TabLayout.DEFAULT
    }

    /** A drop's tier, lowest first. [word] is what the server writes before `DROP!`. */
    enum class LootTier(private val label: String, val word: String) {
        RARE("Rare", "RARE"),
        EPIC("Epic", "EPIC"),
        RNGESUS("RNGesus", "RNGESUS"),
        ;

        override fun toString() = label
    }

    @Serializable
    class Loot {
        @JsonNames("lootAlerts")
        @JvmField
        var rareDropAlert = true

        @JvmField
        var lootAlertTier = LootTier.EPIC
    }

    @Serializable
    class Slayer {
        @JvmField
        var slayerTracker = true

        @JvmField
        var slayerBossAlert = true
    }

    /** Where a feature's HUD block has its plate's top-left corner, as fractions of the scaled screen, and how big it is. */
    @Serializable
    data class Position(val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f)

    @Serializable
    class Hud {
        /** The feature id to its saved position. A block with none is stacked down from the top-left. */
        val positions = HashMap<String, Position>()

        @JvmField
        var theme = Theme.CHARCOAL

        @JsonNames("dragInChat")

        @JvmField
        var chatHudEditor = true

        @JvmField
        var editKey = NO_KEY

        @JvmField
        var scale = 1f

        @JvmField
        var background = true

        @JvmField
        var hideInF3 = false
    }

    @Serializable
    class Contribute {

        @JvmField
        var reportGameData = false
    }
}

/**
 * No key, which the settings screen shows as None. DragSim Utils and Odin open their settings on Right Shift, and a
 * player who runs either would open two screens with one press, so the Settings Key leaves the player to pick one.
 */
private const val NO_KEY = -1
