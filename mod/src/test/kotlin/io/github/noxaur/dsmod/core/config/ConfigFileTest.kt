package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.core.camera.KeyMode
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
     * set away from its default. Each value comes back, and a favorite saved by MoulConfig's path becomes its row's id.
     */
    @Test
    fun `a file the MoulConfig build wrote loads every value`() {
        Files.copy(Path.of(javaClass.getResource("/config/moulconfig.json")!!.toURI()), path)
        val c = ConfigFile(path).load()
        assertEquals(listOf("general.fullbright", "loot.lootAlerts", "hud.scale", "end.editBeam", "general.resetAnimations"), c.favorites)
        assertEquals(listOf("fullbright", "lootAlerts", "hudScale", "beamSections", "resetAnimations"), SettingsTree.favorites(c.favorites))

        assertFalse(c.chat.abilitySpam)
        assertFalse(c.chat.copyChat)

        c.end.run {
            assertFalse(zealotTracker || protectPlacedEyes || dragonTimer || fireballAlert || golemStatus || bossBeacon || bossDrops)
            assertEquals(listOf(Section(20, 60), Section(150, 200)), beamSections)
            assertTrue(beamInFight)
        }

        c.general.run {
            assertFalse(onlyOnDragSim)
            assertEquals(344, settingsKey)
            assertEquals(71, favoriteKey)
            assertTrue(fullbright && explosionParticleHider)
            assertFalse(animations)
            assertEquals(SwingStyle.EXHIBITION, swingStyle)
            assertEquals(listOf(1.5f, 0.75f, 0.25f, -0.5f, 0.1f, 45f, -30f, 12f), listOf(swingSpeed, itemSize, itemX, itemY, itemZ, itemYaw, itemPitch, itemRoll))
            assertFalse(scaleSwing)
            assertTrue(noSwing && noBowSwing && ignoreHaste && noEquipReset)
            assertFalse(equipmentDisplay)
            assertEquals(listOf(EquipmentPiece.PET, EquipmentPiece.HELMET, EquipmentPiece.BOOTS), equipmentPieces)
            assertEquals(EquipmentLayout.COLUMN, equipmentLayout)
            assertEquals(PetLook.NAME, petLook)
            assertEquals(PetPosition.BELOW, petPosition)
            assertEquals(EquipmentLook.HOTBAR, equipmentLook)
            assertEquals(HudDock.RIGHT_OF_HOTBAR, equipmentPlacement)
            assertTrue(inventoryPreview)
            assertEquals(listOf(InventoryRow.HOTBAR, InventoryRow.TOP), inventoryRows)
            assertEquals(InventoryLook.INVENTORY, inventoryLook)
            assertFalse(etherwarpOutline || etherwarpFailed || etherwarpThroughWalls)
            assertEquals(BoxStyle.FILLED, etherwarpStyle)
            assertEquals(3.5f, etherwarpWidth)
            assertTrue(etherwarpWithoutSneak)
            assertFalse(camera)
            assertEquals(listOf(12.5f, 1.2f, -10f, 40f), listOf(cameraDistance, cameraHeight, cameraTilt, cameraSmoothing))
            assertEquals(Views.SKIP_FRONT, cameraViews)
            assertFalse(freeLook)
            assertEquals(4, freeLookKey)
            assertEquals(KeyMode.TOGGLE, freeLookMode)
            assertFalse(commandBinds)
            assertEquals(
                listOf(
                    CommandBind("Warp End", "/warp end", listOf(341, 69), false, Where.MENUS, Area.END, 2, false, true, "Travel"),
                    CommandBind(command = "/pets", keys = listOf(80)),
                ),
                binds,
            )
            assertEquals(listOf("Travel"), bindGroups)
            assertEquals(listOf("Travel"), collapsedGroups)
            assertEquals(listOf("Travel"), offGroups)
            assertEquals(400f, sequenceGap)
            assertFalse(profileViewer || profileNames)
        }

        c.inventory.run {
            assertFalse(rarityMarks)
            assertEquals(RarityPreset.CUSTOM, rarityPreset)
            // MoulConfig's Gson wrote a style as {}, since its fields had no @Expose, so the old file holds no custom look.
            assertEquals(SlotStyle(), inventoryStyle)
            assertEquals(SlotStyle(), hotbarStyle)
            assertFalse(hotbarMatches || hotbarMarks || themedMenus || stableMenus || hideFiller || keepCursor || slotLock)
            assertTrue(markCommon && armorSwapGuard)
            assertEquals(MenuLook.GLASS, menuLook)
            assertEquals(35f, glassOpacity)
            assertEquals(74, slotLockKey)
            assertEquals(3, itemLockKey)
        }

        c.betterUi.run {
            assertFalse(enabled || backButtons || menuTabs)
            assertEquals(listOf(MenuTab(TabEdge.TOP, 2, "bazaar", "minecraft:gold_ingot", "Bazaar"), MenuTab(TabEdge.BOTTOM, 6, item = "", tooltip = "Search", search = true)), tabs)
        }

        assertFalse(c.loot.lootAlerts)
        assertEquals(DsModConfig.LootTier.RNGESUS, c.loot.lootAlertTier)
        assertFalse(c.slayer.slayerTracker || c.slayer.slayerBossAlert)

        c.hud.run {
            assertEquals(mapOf("zealot-tracker" to DsModConfig.Position(0.25f, 0.5f, 1.5f), "dragon-timer" to DsModConfig.Position(0.75f, 0.125f, 0.75f)), positions)
            assertEquals(Theme.VANILLA, theme)
            assertFalse(dragInChat || background)
            assertEquals(1.75f, scale)
        }

        assertTrue(c.contribute.reportGameData)
    }

    @Test
    fun `what is saved loads again, the custom look too`() {
        val c = DsModConfig()
        c.general.cameraDistance = 9f
        c.inventory.inventoryStyle = SlotStyle(gem = true)
        c.hud.positions["boss-drops"] = DsModConfig.Position(0.5f, 0.5f, 2f)
        ConfigFile(path).save(c)
        val back = ConfigFile(path).load()
        assertEquals(ConfigFile.encode(c), ConfigFile.encode(back))
        assertEquals(SlotStyle(gem = true), back.inventory.inventoryStyle)
        assertFalse(dir.resolve("config.json.tmp").exists(), "the temporary file is renamed over the old one")
    }

    @Test
    fun `a missing file, key or enum value takes its default, and an unknown key is passed over`() {
        assertEquals(ConfigFile.encode(DsModConfig()), ConfigFile.encode(ConfigFile(path).load()))
        path.writeText("""{"general":{"fullbright":true,"swingStyle":"GONE","removedSetting":3},"gone":{}}""")
        val file = ConfigFile(path)
        val c = file.load()
        assertTrue(c.general.fullbright)
        assertEquals(SwingStyle.VANILLA, c.general.swingStyle)
        assertEquals(DsModConfig().end.beamSections, c.end.beamSections)
        assertNull(file.unreadable)
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
