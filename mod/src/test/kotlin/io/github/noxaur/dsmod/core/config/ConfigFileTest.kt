package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.camera.Views
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.chat.CopyChat
import io.github.noxaur.dsmod.core.feature.chat.HideCooldownMessages
import io.github.noxaur.dsmod.core.feature.end.BossFightTracker
import io.github.noxaur.dsmod.core.feature.end.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.EyeGuard
import io.github.noxaur.dsmod.core.feature.end.FireballAlert
import io.github.noxaur.dsmod.core.feature.end.ProtectorSpawnBeam
import io.github.noxaur.dsmod.core.feature.end.ProtectorStatus
import io.github.noxaur.dsmod.core.feature.end.ZealotTracker
import io.github.noxaur.dsmod.core.feature.general.Animations
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.feature.general.EquipmentDisplay
import io.github.noxaur.dsmod.core.feature.general.EquipmentLayout
import io.github.noxaur.dsmod.core.feature.general.EquipmentLook
import io.github.noxaur.dsmod.core.feature.general.EquipmentPiece
import io.github.noxaur.dsmod.core.feature.general.EtherwarpOutline
import io.github.noxaur.dsmod.core.feature.general.FreeLook
import io.github.noxaur.dsmod.core.feature.general.Fullbright
import io.github.noxaur.dsmod.core.feature.general.HideExplosions
import io.github.noxaur.dsmod.core.feature.general.InventoryHud
import io.github.noxaur.dsmod.core.feature.general.InventoryLook
import io.github.noxaur.dsmod.core.feature.general.InventoryRow
import io.github.noxaur.dsmod.core.feature.general.PetLook
import io.github.noxaur.dsmod.core.feature.general.PetPosition
import io.github.noxaur.dsmod.core.feature.general.ProfileViewer
import io.github.noxaur.dsmod.core.feature.hud.ChatHudEditor
import io.github.noxaur.dsmod.core.feature.inventory.ArmorSwapGuard
import io.github.noxaur.dsmod.core.feature.inventory.BackButtons
import io.github.noxaur.dsmod.core.feature.inventory.FixedMenuLayout
import io.github.noxaur.dsmod.core.feature.inventory.HideGlassPanes
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.feature.inventory.KeepCursor
import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.feature.loot.RareDropAlert
import io.github.noxaur.dsmod.core.feature.slayer.SlayerBossAlert
import io.github.noxaur.dsmod.core.feature.slayer.SlayerTracker
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.hand.SwingStyle
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.core.world.BoxStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigFileTest {
    private val dir = Files.createTempDirectory("dsmod-config")
    private val path = dir.resolve("config.json")

    /**
     * `config/moulconfig.json` was written by version 0.1.0, which saved through MoulConfig's Gson, with every setting
     * set away from its default. Each value comes back, through the feature that owns it now, and a favorite saved by
     * MoulConfig's path becomes its row's id.
     */
    @Test
    fun `a file the MoulConfig build wrote loads every value`() {
        Files.copy(Path.of(javaClass.getResource("/config/moulconfig.json")!!.toURI()), path)
        val c = ConfigFile(path).load()
        val tree = SettingsTree(c)
        assertEquals(listOf("general.fullbright", "loot.lootAlerts", "hud.scale", "end.editBeam", "general.resetAnimations"), c.favorites)
        // Loot Alerts became Rare Drop Alert, and its star followed its former key to the feature's row.
        assertEquals(listOf("fullbright", "rare-drop-alert", "hudScale", "beamSections", "resetAnimations"), SettingsTree.favorites(c.favorites))

        assertFalse(tree.feature<HideCooldownMessages>().feature.enabled)
        assertFalse(tree.feature<CopyChat>().feature.enabled)

        assertFalse(
            tree.feature<ZealotTracker>().feature.enabled || tree.feature<EyeGuard>().feature.enabled || tree.feature<DragonTimer>().feature.enabled ||
                tree.feature<FireballAlert>().feature.enabled || tree.feature<ProtectorStatus>().feature.enabled || tree.feature<BossFightTracker>().feature.enabled,
        )
        tree.feature<ProtectorSpawnBeam>().run {
            assertFalse(feature.enabled)
            assertEquals(listOf(Section(20, 60), Section(150, 200)), sections.value)
            assertTrue(inFight.value)
        }

        c.general.run {
            assertFalse(onlyOnDragSim)
            assertEquals(344, settingsKey)
            assertEquals(71, favoriteKey)
        }
        assertTrue(tree.feature<Fullbright>().feature.enabled && tree.feature<HideExplosions>().feature.enabled)
        tree.feature<Animations>().run {
            assertFalse(feature.enabled)
            assertEquals(SwingStyle.EXHIBITION, swingStyle.value)
            assertEquals(listOf(1.5f, 0.75f, 0.25f, -0.5f, 0.1f, 45f, -30f, 12f), listOf(swingSpeed, itemSize, itemX, itemY, itemZ, itemYaw, itemPitch, itemRoll).map { it.value })
            assertFalse(scaleSwing.value)
            assertTrue(noSwing.value && noBowSwing.value && ignoreHaste.value && noEquipReset.value)
        }
        tree.feature<EquipmentDisplay>().run {
            assertFalse(feature.enabled)
            assertEquals(listOf(EquipmentPiece.PET, EquipmentPiece.HELMET, EquipmentPiece.BOOTS), pieces.value)
            assertEquals(EquipmentLayout.COLUMN, layout.value)
            assertEquals(PetLook.NAME, petLook.value)
            assertEquals(PetPosition.BELOW, petPosition.value)
            assertEquals(EquipmentLook.HOTBAR, look.value)
            assertEquals(HudDock.RIGHT_OF_HOTBAR, placement.value)
        }
        tree.feature<InventoryHud>().run {
            assertTrue(feature.enabled)
            assertEquals(listOf(InventoryRow.HOTBAR, InventoryRow.TOP), shownRows.value)
            assertEquals(InventoryLook.INVENTORY, look.value)
        }
        tree.feature<EtherwarpOutline>().run {
            assertFalse(feature.enabled || failed.value || throughWalls.value)
            assertEquals(BoxStyle.FILLED, style.value)
            assertEquals(3.5f, width.value)
            assertTrue(withoutSneak.value)
        }
        tree.feature<Camera>().run {
            assertFalse(feature.enabled)
            assertEquals(listOf(12.5f, 1.2f, -10f, 40f), listOf(distance, height, tilt, smoothing).map { it.value })
            assertEquals(Views.SKIP_FRONT, views.value)
        }
        tree.feature<FreeLook>().run {
            assertFalse(feature.enabled)
            assertEquals(4, key.value)
            assertEquals(KeyMode.TOGGLE, mode.value)
        }
        tree.feature<CommandBinds>().run {
            assertFalse(feature.enabled)
            assertEquals(
                listOf(
                    CommandBind("Warp End", "/warp end", listOf(341, 69), false, Where.MENUS, Area.END, 2, false, true, "Travel"),
                    CommandBind(command = "/pets", keys = listOf(80)),
                ),
                binds.value,
            )
            assertEquals(listOf("Travel"), groups.value)
            assertEquals(listOf("Travel"), collapsedGroups.value)
            assertEquals(listOf("Travel"), offGroups.value)
            assertEquals(400f, sequenceGap.value)
        }
        tree.feature<ProfileViewer>().run { assertFalse(feature.enabled || clickNames.value) }

        tree.feature<ItemRarity>().run {
            assertFalse(feature.enabled)
            assertEquals(RarityPreset.CUSTOM, preset.value)
            // MoulConfig's Gson wrote a style as {}, since its fields had no @Expose, so the old file holds no custom look.
            assertEquals(SlotStyle(), inventoryStyle.value)
            assertEquals(SlotStyle(), hotbarStyle.value)
            assertFalse(hotbarMatches.value || hotbarMarks.value)
            assertTrue(markCommon.value)
        }
        tree.feature<MenuTheme>().run {
            assertFalse(feature.enabled)
            assertEquals(MenuLook.GLASS, menuLook.value)
            assertEquals(35f, glassOpacity.value)
        }
        assertFalse(tree.feature<FixedMenuLayout>().feature.enabled || tree.feature<HideGlassPanes>().feature.enabled || tree.feature<KeepCursor>().feature.enabled)
        tree.feature<SlotLock>().run {
            assertFalse(feature.enabled)
            assertEquals(74, slotLockKey.value)
            assertEquals(3, itemLockKey.value)
        }
        assertTrue(tree.feature<ArmorSwapGuard>().feature.enabled)

        assertFalse(c.betterUi.enabled || tree.feature<BackButtons>().feature.enabled || tree.feature<MenuTabs>().feature.enabled)
        assertEquals(
            listOf(MenuTab(TabEdge.TOP, 2, "bazaar", "minecraft:gold_ingot", "Bazaar"), MenuTab(TabEdge.BOTTOM, 6, item = "", tooltip = "Search", search = true)),
            tree.feature<MenuTabs>().tabs.value,
        )

        tree.feature<RareDropAlert>().run {
            assertFalse(feature.enabled)
            assertEquals(RareDropAlert.LootTier.RNGESUS, least.value)
        }
        assertFalse(tree.feature<SlayerTracker>().feature.enabled || tree.feature<SlayerBossAlert>().feature.enabled)

        c.hud.run {
            assertEquals(mapOf("zealot-tracker" to DsModConfig.Position(0.25f, 0.5f, 1.5f), "dragon-timer" to DsModConfig.Position(0.75f, 0.125f, 0.75f)), positions)
            assertEquals(Theme.VANILLA, theme)
            assertFalse(background)
            assertEquals(1.75f, scale)
        }
        assertFalse(tree.feature<ChatHudEditor>().feature.enabled)

        assertTrue(c.contribute.reportGameData)
    }

    @Test
    fun `what is saved loads again, the custom look too`() {
        val c = DsModConfig()
        val tree = SettingsTree(c)
        tree.feature<Camera>().distance.value = 9f
        tree.feature<ItemRarity>().inventoryStyle.value = SlotStyle(gem = true)
        c.hud.positions["boss-fight-tracker"] = DsModConfig.Position(0.5f, 0.5f, 2f)
        ConfigFile(path).save(c)
        val back = ConfigFile(path).load()
        // A slot is written once its feature is built, so both sides are read through a tree.
        val loaded = SettingsTree(back)
        assertEquals(ConfigFile.encode(c), ConfigFile.encode(back))
        assertEquals(9f, loaded.feature<Camera>().distance.value)
        assertEquals(SlotStyle(gem = true), loaded.feature<ItemRarity>().inventoryStyle.value)
        assertFalse(dir.resolve("config.json.tmp").exists(), "the temporary file is renamed over the old one")
    }

    @Test
    fun `a missing file, key or enum value takes its default, and an unknown key is passed over`() {
        assertEquals(ConfigFile.encode(DsModConfig()), ConfigFile.encode(ConfigFile(path).load()))
        path.writeText("""{"general":{"fullbright":true,"swingStyle":"GONE","removedSetting":3},"gone":{}}""")
        val file = ConfigFile(path)
        val tree = SettingsTree(file.load())
        assertTrue(tree.feature<Fullbright>().feature.enabled)
        assertEquals(SwingStyle.VANILLA, tree.feature<Animations>().swingStyle.value)
        assertEquals(SettingsTree().feature<ProtectorSpawnBeam>().sections.value, tree.feature<ProtectorSpawnBeam>().sections.value)
        assertNull(file.unreadable)
    }

    /** The file keeps MoulConfig's keys: each feature's settings sit in its section, and its switch under its field's old name. */
    @Test
    fun `a saved file holds every feature's keys in its section, and a switch under the camel case of its id`() {
        val tree = SettingsTree()
        val saved = Json.parseToJsonElement(ConfigFile.encode(tree.config)).jsonObject
        for (feature in tree.features) {
            val camel = feature.id.split('-').withIndex().joinToString("") { (i, word) -> if (i == 0) word else word.replaceFirstChar(Char::uppercase) }
            assertEquals(camel, feature.switch.field.substringAfter('.'), feature.id)
        }
        for (store in Store.entries) {
            val section = saved[store.key]?.jsonObject.orEmpty()
            for (slot in tree.config.stored(store).all) assertTrue(slot.key in section, "${slot.key} is not under ${store.key}")
        }
        assertTrue("fullbright" in saved.getValue("general").jsonObject)
        assertTrue("beamSections" in saved.getValue("end").jsonObject)
    }

    @Test
    fun `a file that does not parse is kept beside it, and the settings start from their defaults`() {
        path.writeText("{not json")
        val file = ConfigFile(path)
        val c = file.load()
        assertEquals(ConfigFile.encode(DsModConfig()), ConfigFile.encode(c))
        val kept = dir.listDirectoryEntries("config.json.corrupt-*").single()
        assertEquals(kept, file.unreadable)
        assertEquals("{not json", kept.readText())
        // A save writes a new file and leaves the kept one alone.
        file.save(c)
        assertEquals("{not json", kept.readText())
        assertTrue(path.exists())
    }
}
