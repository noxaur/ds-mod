package io.github.noxaur.dsmod.core.config

import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.camera.ThirdPerson
import io.github.noxaur.dsmod.core.camera.Views
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.general.EquipmentLayout
import io.github.noxaur.dsmod.core.feature.general.EquipmentLook
import io.github.noxaur.dsmod.core.feature.general.EquipmentPiece
import io.github.noxaur.dsmod.core.feature.general.InventoryLook
import io.github.noxaur.dsmod.core.feature.general.InventoryRow
import io.github.noxaur.dsmod.core.feature.general.PetLook
import io.github.noxaur.dsmod.core.feature.general.PetPosition
import io.github.noxaur.dsmod.core.hand.SwingStyle
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.core.world.BoxStyle

/**
 * The settings show categories and options in the order they are declared: what applies everywhere first, then each area
 * of the game, and Contribute last. MoulConfig reads the fields directly, so every one it shows needs `@JvmField`, and
 * Gson saves only the ones with `@Expose`.
 */
class DsModConfig : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("ds-mod")

    /** The paths of the settings the player starred, which the Favorites category lists. */
    @Expose
    @JvmField
    var favorites: List<String> = emptyList()

    @Expose
    @Category(name = "General", desc = "The camera, your binds and the world.")
    @JvmField
    val general = General()

    @Expose
    @Category(name = "Interface", desc = "How ds-mod's HUD and screens look.")
    @JvmField
    val hud = Hud()

    @Expose
    @Category(name = "Chat", desc = "What reaches your chat.")
    @JvmField
    val chat = Chat()

    @Expose
    @Category(name = "Inventory", desc = "How menus look, and what keeps items safe.")
    @JvmField
    val inventory = Inventory()

    @Expose
    @Category(name = "Better UI", desc = "Tabs, a search and buttons for menus.")
    @JvmField
    val betterUi = BetterUi()

    @Expose
    @Category(name = "Loot", desc = "Your drops.")
    @JvmField
    val loot = Loot()

    @Expose
    @Category(name = "The End", desc = "Zealots, dragons and the Protector.")
    @JvmField
    val end = End()

    @Expose
    @Category(name = "Slayers", desc = "Your slayer quests and bosses.")
    @JvmField
    val slayer = Slayer()

    @Expose
    @Category(name = "Contribute", desc = "Record the game for ds-mod's developers.")
    @JvmField
    val contribute = Contribute()

    class Chat {
        @Expose
        @ConfigOption(name = "Ability Spam", desc = "Hide the message that an ability is on cooldown.")
        @ConfigEditorBoolean
        @JvmField
        var abilitySpam = true

        @Expose
        @ConfigOption(name = "Copy Chat", desc = "Right-click messages to copy them as text, format codes or JSON.")
        @ConfigEditorBoolean
        @JvmField
        var copyChat = true
    }

    class End {
        @Expose
        @ConfigOption(name = "Zealot Tracker", desc = "Count your Summoning Eyes and zealot kills, per hour and all-time.")
        @ConfigEditorBoolean
        @JvmField
        var zealotTracker = true

        @Expose
        @ConfigOption(name = "Protect Placed Eyes", desc = "Ignore a quick second click that would take a placed eye back.")
        @ConfigEditorBoolean
        @JvmField
        var protectPlacedEyes = true

        @Expose
        @ConfigOption(name = "Dragon Timer", desc = "Show the egg timer, the eyes placed and the dragon's time alive.")
        @ConfigEditorBoolean
        @JvmField
        var dragonTimer = true

        @Expose
        @ConfigOption(name = "Fireball Alert", desc = "Alert you when a dragon is about to throw fireballs.")
        @ConfigEditorBoolean
        @JvmField
        var fireballAlert = true
    

        @Expose
        @ConfigOption(name = "Golem Status", desc = "Show the Protector's stage and the kills left until it emerges.")
        @ConfigEditorBoolean
        @JvmField
        var golemStatus = true

        @Expose
        @ConfigOption(name = "Boss Beacon", desc = "Show a beam where the Endstone Protector will emerge.")
        @ConfigEditorBoolean
        @JvmField
        var bossBeacon = true

        /** The parts of the Protector's cycle that show the beam, in zealots killed since the last defeat, out of 200. */
        @Expose
        var beamSections: List<Section> = listOf(Section(175, 200))

        @Expose
        var beamInFight = false

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Beam Timing", desc = "Pick when in the Protector's cycle the beam shows.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var editBeam: Runnable = Runnable {}

        @Expose
        @ConfigOption(name = "Boss Drops", desc = "Show your rank, damage and loot from the last boss fight.")
        @ConfigEditorBoolean
        @JvmField
        var bossDrops = true
    }

    class General {
        @Expose
        @ConfigOption(name = "DragSim Only", desc = "Run ds-mod only on dragsim.co. Turn it off to use it on any server.")
        @ConfigEditorBoolean
        @JvmField
        var onlyOnDragSim = true

        /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as MoulConfig's editor writes it. */
        @Expose
        @ConfigOption(name = "Settings Key", desc = "Pick a key that opens these settings, as /dsmod does.")
        @ConfigEditorKeybind(defaultKey = NO_KEY)
        @JvmField
        var settingsKey = NO_KEY

        /** A key's GLFW code or a mouse button, as for [settingsKey]. */
        @Expose
        @ConfigOption(name = "Favorite Key", desc = "Pick a key that stars the setting under your mouse.")
        @ConfigEditorKeybind(defaultKey = KEY_F)
        @JvmField
        var favoriteKey = KEY_F

        @Expose
        @ConfigOption(name = "Fullbright", desc = "Light caves and nights as brightly as day.")
        @ConfigEditorBoolean
        @JvmField
        var fullbright = false

        @Expose
        @ConfigOption(name = "Explosion Particle Hider", desc = "Hide the clouds that explosions throw up.")
        @ConfigEditorBoolean
        @JvmField
        var explosionParticleHider = false

        @Expose
        @ConfigOption(name = "Animations", desc = "Shape how your held item swings and sits with the settings below.")
        @ConfigEditorBoolean
        @JvmField
        var animations = true

        @Expose
        @ConfigOption(name = "Swing Style", desc = "Pick how your held item moves when you swing.")
        @ConfigEditorDropdown
        @JvmField
        var swingStyle = SwingStyle.VANILLA

        @Expose
        @ConfigOption(name = "Swing Speed", desc = "Speed your swing up, or slow it down below 1.")
        @ConfigEditorSlider(minValue = 0.1f, maxValue = 3f, minStep = 0.1f)
        @JvmField
        var swingSpeed = 1f

        @Expose
        @ConfigOption(name = "Item Size", desc = "Grow or shrink your held item.")
        @ConfigEditorSlider(minValue = 0.1f, maxValue = 2f, minStep = 0.05f)
        @JvmField
        var itemSize = 1f

        @ConfigOption(name = "Item Position", desc = "Move and turn your held item.")
        @ConfigEditorAccordion(id = ITEM_POSITION)
        @JvmField
        var itemPosition = false

        @Expose
        @ConfigOption(name = "Item X", desc = "Move your held item out to the side, or in below zero.")
        @ConfigEditorSlider(minValue = -1f, maxValue = 1f, minStep = 0.05f)
        @ConfigAccordionId(id = ITEM_POSITION)
        @JvmField
        var itemX = 0f

        @Expose
        @ConfigOption(name = "Item Y", desc = "Raise your held item, or lower it below zero.")
        @ConfigEditorSlider(minValue = -1f, maxValue = 1f, minStep = 0.05f)
        @ConfigAccordionId(id = ITEM_POSITION)
        @JvmField
        var itemY = 0f

        @Expose
        @ConfigOption(name = "Item Z", desc = "Push your held item away, or bring it nearer below zero.")
        @ConfigEditorSlider(minValue = -1f, maxValue = 1f, minStep = 0.05f)
        @ConfigAccordionId(id = ITEM_POSITION)
        @JvmField
        var itemZ = 0f

        @Expose
        @ConfigOption(name = "Item Yaw", desc = "Turn your held item left or right about its grip, in degrees.")
        @ConfigEditorSlider(minValue = -180f, maxValue = 180f, minStep = 1f)
        @ConfigAccordionId(id = ITEM_POSITION)
        @JvmField
        var itemYaw = 0f

        @Expose
        @ConfigOption(name = "Item Pitch", desc = "Tip your held item forward or back about its grip, in degrees.")
        @ConfigEditorSlider(minValue = -180f, maxValue = 180f, minStep = 1f)
        @ConfigAccordionId(id = ITEM_POSITION)
        @JvmField
        var itemPitch = 0f

        @Expose
        @ConfigOption(name = "Item Roll", desc = "Tilt your held item sideways about its grip, in degrees.")
        @ConfigEditorSlider(minValue = -180f, maxValue = 180f, minStep = 1f)
        @ConfigAccordionId(id = ITEM_POSITION)
        @JvmField
        var itemRoll = 0f

        @ConfigOption(name = "Swing Options", desc = "Fine-tune how your held item swings and dips.")
        @ConfigEditorAccordion(id = SWING_OPTIONS)
        @JvmField
        var swingOptions = false

        @Expose
        @ConfigOption(name = "Scale Swing", desc = "Make the swing reach as far as Item Size makes the item big.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = SWING_OPTIONS)
        @JvmField
        var scaleSwing = true

        @Expose
        @ConfigOption(name = "No Swing", desc = "Keep your held item still when you swing.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = SWING_OPTIONS)
        @JvmField
        var noSwing = false

        @Expose
        @ConfigOption(name = "No Bow Swing", desc = "Keep a bow still when you swing it, as when it shoots on a click.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = SWING_OPTIONS)
        @JvmField
        var noBowSwing = false

        @Expose
        @ConfigOption(name = "Ignore Haste", desc = "Swing at the same pace under Haste or Mining Fatigue.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = SWING_OPTIONS)
        @JvmField
        var ignoreHaste = false

        @Expose
        @ConfigOption(name = "No Equip Reset", desc = "Keep your held item up instead of dipping after a swing or use.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = SWING_OPTIONS)
        @JvmField
        var noEquipReset = false

        @ConfigOption(name = "Reset Animations", desc = "Put every Animations setting back to the game's own.")
        @ConfigEditorButton(buttonText = "Reset")
        @JvmField
        var resetAnimations: Runnable = Runnable(::resetAnimations)

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

        @Expose
        @ConfigOption(name = "Ability Cooldowns", desc = "Time your item abilities and Riftwalker's charged hit.")
        @ConfigEditorBoolean
        @JvmField
        var abilityCooldowns = true

        @Expose
        @ConfigOption(name = "Cooldown List", desc = "List the abilities you are waiting on, on the HUD.")
        @ConfigEditorBoolean
        @JvmField
        var cooldownList = true

        @Expose
        @ConfigOption(name = "Cooldown on Items", desc = "Drain a wash over the item and count down its seconds.")
        @ConfigEditorBoolean
        @JvmField
        var cooldownOnItems = true

        @Expose
        @ConfigOption(name = "Shortest Listed", desc = "List only cooldowns at least this many seconds long.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 20f, minStep = 0.5f)
        @JvmField
        var cooldownShortest = 2f

        @Expose
        @ConfigOption(name = "Ready Alert", desc = "Alert you when a long cooldown is over.")
        @ConfigEditorBoolean
        @JvmField
        var cooldownReadyAlert = false

        @Expose
        @ConfigOption(name = "Alert From", desc = "Alert only for cooldowns at least this many seconds long.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 120f, minStep = 1f)
        @JvmField
        var cooldownAlertFrom = 20f

        @Expose
        @ConfigOption(name = "Equipment Display", desc = "Show your armor and pet on the HUD.")
        @ConfigEditorBoolean
        @JvmField
        var equipmentDisplay = true

        @Expose
        @ConfigOption(name = "Equipment Pieces", desc = "Pick the pieces to show, and drag them into order.")
        @ConfigEditorDraggableList(requireNonEmpty = true)
        @JvmField
        var equipmentPieces: MutableList<EquipmentPiece> = EquipmentPiece.entries.toMutableList()

        @Expose
        @ConfigOption(name = "Equipment Layout", desc = "Line the pieces up in a row or a column.")
        @ConfigEditorDropdown
        @JvmField
        var equipmentLayout = EquipmentLayout.ROW

        @Expose
        @ConfigOption(name = "Pet Look", desc = "Show your pet as its head, or as its name and level.")
        @ConfigEditorDropdown
        @JvmField
        var petLook = PetLook.ICON

        @Expose
        @ConfigOption(name = "Pet Position", desc = "Put your pet among the armor, or on a row above or below it.")
        @ConfigEditorDropdown
        @JvmField
        var petPosition = PetPosition.WITH_ARMOR

        @Expose
        @ConfigOption(name = "Equipment Look", desc = "Show the pieces on the plate, on nothing, or in hotbar slots.")
        @ConfigEditorDropdown
        @JvmField
        var equipmentLook = EquipmentLook.PLATE

        @Expose
        @ConfigOption(name = "Equipment Placement", desc = "Keep the pieces where you put them, or beside your hotbar.")
        @ConfigEditorDropdown
        @JvmField
        var equipmentPlacement = HudDock.FREE

        @Expose
        @ConfigOption(name = "Inventory Preview", desc = "Show a grid of the items in your inventory on the HUD.")
        @ConfigEditorBoolean
        @JvmField
        var inventoryPreview = false

        @Expose
        @ConfigOption(name = "Inventory Rows", desc = "Pick the rows to show, and drag them into order.")
        @ConfigEditorDraggableList(requireNonEmpty = true)
        @JvmField
        var inventoryRows: MutableList<InventoryRow> = mutableListOf(InventoryRow.TOP, InventoryRow.MIDDLE, InventoryRow.BOTTOM)

        @Expose
        @ConfigOption(name = "Inventory Look", desc = "Show the items on the plate, on nothing, or as your inventory.")
        @ConfigEditorDropdown
        @JvmField
        var inventoryLook = InventoryLook.PLATE

        @Expose
        @ConfigOption(name = "Etherwarp Outline", desc = "Box the block Etherwarp will take you to while you sneak.")
        @ConfigEditorBoolean
        @JvmField
        var etherwarpOutline = true

        @Expose
        @ConfigOption(name = "Etherwarp Style", desc = "Draw the box as edges, a tinted fill, or both.")
        @ConfigEditorDropdown
        @JvmField
        var etherwarpStyle = BoxStyle.BOTH

        @Expose
        @ConfigOption(name = "Etherwarp Width", desc = "Set how thick the box's edges are.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 5f, minStep = 0.5f)
        @JvmField
        var etherwarpWidth = 2f

        @Expose
        @ConfigOption(name = "Failed Warps", desc = "Box a block you can't warp to as well, and say why.")
        @ConfigEditorBoolean
        @JvmField
        var etherwarpFailed = true

        @Expose
        @ConfigOption(name = "Box Through Walls", desc = "Show the whole box, even behind the blocks in front of it.")
        @ConfigEditorBoolean
        @JvmField
        var etherwarpThroughWalls = true

        @Expose
        @ConfigOption(name = "Box Without Sneak", desc = "Show the box before you sneak, too.")
        @ConfigEditorBoolean
        @JvmField
        var etherwarpWithoutSneak = false

        @Expose
        @ConfigOption(name = "Camera", desc = "Shape the third-person view with the settings below.")
        @ConfigEditorBoolean
        @JvmField
        var camera = true

        @Expose
        @ConfigOption(name = "Camera Distance", desc = "Set how many blocks the third-person camera sits from you.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 50f, minStep = 0.5f)
        @JvmField
        var cameraDistance = ThirdPerson.DISTANCE

        @Expose
        @ConfigOption(name = "Camera Height", desc = "Raise the third-person camera, or lower it below zero.")
        @ConfigEditorSlider(minValue = -2f, maxValue = 3f, minStep = 0.1f)
        @JvmField
        var cameraHeight = 0f

        @Expose
        @ConfigOption(name = "Camera Tilt", desc = "Angle the third-person camera down, or up below zero.")
        @ConfigEditorSlider(minValue = -45f, maxValue = 45f, minStep = 1f)
        @JvmField
        var cameraTilt = 0f

        @Expose
        @ConfigOption(name = "F5 Views", desc = "Pick which views F5 goes through.")
        @ConfigEditorDropdown
        @JvmField
        var cameraViews = Views.ALL

        @Expose
        @ConfigOption(name = "Camera Smoothing", desc = "Slide the camera out when you leave first person.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
        @JvmField
        var cameraSmoothing = 0f

        @Expose
        @ConfigOption(name = "Free Look", desc = "Look around in third person while you keep facing ahead.")
        @ConfigEditorBoolean
        @JvmField
        var freeLook = true

        /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as MoulConfig's editor writes it. */
        @Expose
        @ConfigOption(name = "Free Look Key", desc = "Pick the key or mouse button for Free Look.")
        @ConfigEditorKeybind(defaultKey = LEFT_ALT)
        @JvmField
        var freeLookKey = LEFT_ALT

        @Expose
        @ConfigOption(name = "Free Look Mode", desc = "Pick Hold to look while the key is down, or Toggle.")
        @ConfigEditorDropdown
        @JvmField
        var freeLookMode = KeyMode.HOLD

        @Expose
        @ConfigOption(name = "Command Binds", desc = "Send a command, such as /warp end, with a key or a few keys.")
        @ConfigEditorBoolean
        @JvmField
        var commandBinds = true

        /** Every bind, in the order the player put them, each naming its group. */
        @Expose
        var binds: List<CommandBind> = emptyList()

        /** The groups after the first, which is unnamed, in the order the player made them. */
        @Expose
        var bindGroups: List<String> = emptyList()

        /** The groups whose binds are folded away in the list. */
        @Expose
        var collapsedGroups: List<String> = emptyList()

        /** The groups switched off, whose binds send nothing whatever their own switch says. */
        @Expose
        var offGroups: List<String> = emptyList()

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Edit Command Binds", desc = "Add your binds, pick their keys and choose where each works.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var editBinds: Runnable = Runnable {}

        @Expose
        @ConfigOption(name = "Key Sequence Gap", desc = "Set how many milliseconds a key sequence waits for its next key.")
        @ConfigEditorSlider(minValue = 50f, maxValue = 1000f, minStep = 50f)
        @JvmField
        var sequenceGap = 250f

        @Expose
        @ConfigOption(name = "Profile Viewer", desc = "Look up any player's profile with /pv and their name.")
        @ConfigEditorBoolean
        @JvmField
        var profileViewer = true

        @Expose
        @ConfigOption(name = "Click Names", desc = "Click a player's name in chat to open their profile.")
        @ConfigEditorBoolean
        @JvmField
        var profileNames = true

        private companion object {
            /** GLFW's code for the left Alt key. */
            const val LEFT_ALT = 342

            /**
             * No key. DragSim Utils and Odin open on Right Shift, and a player who runs either would open two
             * screens with one press, so the player picks a key of their own.
             */
            const val NO_KEY = -1

            /** GLFW's code for F, NotEnoughUpdates' key for a favorite. */
            const val KEY_F = 70

            /** The folds in General that hold Animations' finer settings. */
            const val ITEM_POSITION = 0
            const val SWING_OPTIONS = 1
        }
    }

    class Inventory {
        @Expose
        @ConfigOption(name = "Rarity Marks", desc = "Mark each item's slot in the color of its rarity.")
        @ConfigEditorBoolean
        @JvmField
        var rarityMarks = true

        @Expose
        @ConfigOption(name = "Rarity Style", desc = "Pick a look for the marks, or Custom for your own.")
        @ConfigEditorDropdown
        @JvmField
        var rarityPreset = RarityPreset.GILDED

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Edit Style", desc = "Build your own look for the marks, on your own items.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var editRarityStyle: Runnable = Runnable {}

        /** The player's own style for every container, their inventory included, shown while [rarityPreset] is Custom. */
        @Expose
        @JvmField
        var inventoryStyle = SlotStyle()

        @Expose
        @JvmField
        var hotbarStyle = SlotStyle()

        /** While true the hotbar is drawn in [inventoryStyle], and [hotbarStyle] waits for the player to unlink them. */
        @Expose
        @JvmField
        var hotbarMatches = true

        @Expose
        @JvmField
        var hotbarMarks = true

        @Expose
        @JvmField
        var markCommon = false

        /** The style containers show: the preset's, or the player's own under Custom. */
        fun containerStyle(): SlotStyle = rarityPreset.style ?: inventoryStyle

        @Expose
        @ConfigOption(name = "Themed Menus", desc = "Draw menus and plain tooltips in the Theme under Interface.")
        @ConfigEditorBoolean
        @JvmField
        var themedMenus = true

        @Expose
        @ConfigOption(name = "Menu Look", desc = "Draw themed menus solid, or as glass that shows the world.")
        @ConfigEditorDropdown
        @JvmField
        var menuLook = MenuLook.SOLID

        @Expose
        @ConfigOption(name = "Glass Opacity", desc = "Set how dark the glass is, from clear at 0 to solid at 100.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
        @JvmField
        var glassOpacity = 60f

        @Expose
        @ConfigOption(name = "Stable Menus", desc = "Keep Go Back, Close and your inventory in place in every menu.")
        @ConfigEditorBoolean
        @JvmField
        var stableMenus = true

        @Expose
        @ConfigOption(name = "Hide Filler", desc = "Hide the blank glass panes that pad out menus.")
        @ConfigEditorBoolean
        @JvmField
        var hideFiller = true

        @Expose
        @ConfigOption(name = "Keep Cursor", desc = "Leave the cursor where it was when one menu opens another.")
        @ConfigEditorBoolean
        @JvmField
        var keepCursor = true

        @Expose
        @ConfigOption(name = "Slot Lock", desc = "Lock inventory slots and items so a misclick cannot move them.")
        @ConfigEditorBoolean
        @JvmField
        var slotLock = true

        /** A key's GLFW code, a mouse button from 0 to 9, or -1 for none, as MoulConfig's editor writes it. */
        @Expose
        @ConfigOption(name = "Slot Lock Key", desc = "Pick the key or mouse button that locks the slot under the mouse.")
        @ConfigEditorKeybind(defaultKey = KEY_L)
        @JvmField
        var slotLockKey = KEY_L

        @Expose
        @ConfigOption(name = "Item Lock Key", desc = "Pick the key that locks an item that does not stack.")
        @ConfigEditorKeybind(defaultKey = KEY_K)
        @JvmField
        var itemLockKey = KEY_K

        @Expose
        @ConfigOption(name = "Armor Swap Guard", desc = "Stop a right click from swapping on armor. Sneak to swap.")
        @ConfigEditorBoolean
        @JvmField
        var armorSwapGuard = false

        private companion object {
            /** GLFW's codes for the K and L keys. */
            const val KEY_K = 75
            const val KEY_L = 76
        }
    }

    class BetterUi {
        @Expose
        @ConfigOption(name = "Better UI", desc = "Turn everything below on or off at once.")
        @ConfigEditorBoolean
        @JvmField
        var enabled = true

        @Expose
        @ConfigOption(name = "Back Buttons", desc = "Add Go Back to menus without one, by resending your last command.")
        @ConfigEditorBoolean
        @JvmField
        var backButtons = true

        @Expose
        @ConfigOption(name = "Menu Tabs", desc = "Add tabs that send your commands, and a search, to every menu.")
        @ConfigEditorBoolean
        @JvmField
        var menuTabs = true

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Edit Tabs", desc = "Add, move and change your tabs.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var editTabs: Runnable = Runnable {}

        /** The tabs the player set up, in the editor. */
        @Expose
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

    class Loot {
        @Expose
        @ConfigOption(name = "Loot Alerts", desc = "Alert you to drops of the chosen tier or better.")
        @ConfigEditorBoolean
        @JvmField
        var lootAlerts = true

        @Expose
        @ConfigOption(name = "Alert Tier", desc = "Pick the lowest drop tier that alerts you.")
        @ConfigEditorDropdown
        @JvmField
        var lootAlertTier = LootTier.EPIC
    }

    class Slayer {
        @Expose
        @ConfigOption(name = "Slayer Tracker", desc = "Count the slayer bosses you slay and time each from spawn to slain.")
        @ConfigEditorBoolean
        @JvmField
        var slayerTracker = true

        @Expose
        @ConfigOption(name = "Slayer Boss Alert", desc = "Alert you when your slayer boss spawns.")
        @ConfigEditorBoolean
        @JvmField
        var slayerBossAlert = true
    }

    /** Where a feature's HUD block has its plate's top-left corner, as fractions of the scaled screen, and how big it is. */
    data class Position(@Expose val x: Float = 0f, @Expose val y: Float = 0f, @Expose val scale: Float = 1f)

    class Hud {
        /** The feature id to its saved position. A block with none is stacked down from the top-left. */
        @Expose
        val positions = HashMap<String, Position>()

        @Expose
        @ConfigOption(name = "Theme", desc = "Pick the colors of ds-mod's screens and controls.")
        @ConfigEditorDropdown
        @JvmField
        var theme = Theme.CHARCOAL

        /** The edge sets this to open the editor, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Move HUD", desc = "Drag a block to move it, and scroll over it to resize it.")
        @ConfigEditorButton(buttonText = "Edit")
        @JvmField
        var edit: Runnable = Runnable {}

        @Expose
        @ConfigOption(name = "Drag In Chat", desc = "Drag HUD blocks with chat open, and scroll over one to resize it.")
        @ConfigEditorBoolean
        @JvmField
        var dragInChat = true

        @Expose
        @ConfigOption(name = "HUD Scale", desc = "Make every HUD block bigger or smaller.")
        @ConfigEditorSlider(minValue = 0.5f, maxValue = 3f, minStep = 0.25f)
        @JvmField
        var scale = 1f

        @Expose
        @ConfigOption(name = "HUD Background", desc = "Show a dark plate behind the HUD, so it reads over a bright world.")
        @ConfigEditorBoolean
        @JvmField
        var background = true
    }

    class Contribute {
        /** The edge sets this to open the requests, so the config itself needs no Minecraft. */
        @ConfigOption(name = "Contribute", desc = "See what ds-mod's developers ask players to record.")
        @ConfigEditorButton(buttonText = "Open")
        @JvmField
        var open: Runnable = Runnable {}

        @Expose
        @ConfigOption(name = "Report Game Data", desc = "Send game data new to ds-mod to its developers, names removed.")
        @ConfigEditorBoolean
        @JvmField
        var reportGameData = false
    }
}
