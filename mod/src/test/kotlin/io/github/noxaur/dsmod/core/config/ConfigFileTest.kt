package io.github.noxaur.dsmod.core.config

import io.github.noxaur.dsmod.harness.resource
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.camera.Views
import io.github.noxaur.dsmod.core.container.MarkStyle
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.feature.utilities.chat.copychat.CopyChat
import io.github.noxaur.dsmod.core.feature.combat.abilitycooldowns.hidecooldownmessages.HideCooldownMessages
import io.github.noxaur.dsmod.core.feature.contribute.reportgamedata.ReportGameData
import io.github.noxaur.dsmod.core.feature.end.trackers.bossfighttracker.BossFightTracker
import io.github.noxaur.dsmod.core.feature.end.dragon.dragontimer.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.dragon.eyeguard.EyeGuard
import io.github.noxaur.dsmod.core.feature.end.dragon.fireballalert.FireballAlert
import io.github.noxaur.dsmod.core.feature.end.protector.protectorspawnbeam.ProtectorSpawnBeam
import io.github.noxaur.dsmod.core.feature.end.protector.protectorstatus.ProtectorStatus
import io.github.noxaur.dsmod.core.feature.end.trackers.zealottracker.ZealotTracker
import io.github.noxaur.dsmod.core.feature.visuals.animations.Animations
import io.github.noxaur.dsmod.core.feature.utilities.autoclick.AutoClick
import io.github.noxaur.dsmod.core.feature.visuals.camera.thirdperson.Camera
import io.github.noxaur.dsmod.core.feature.utilities.commandbinds.CommandBinds
import io.github.noxaur.dsmod.core.feature.general.mouse.ctrlclick.CtrlClick
import io.github.noxaur.dsmod.core.feature.general.dsmod.dragsimonly.DragSimOnly
import io.github.noxaur.dsmod.core.feature.hud.equipmentdisplay.EquipmentDisplay
import io.github.noxaur.dsmod.core.feature.hud.equipmentdisplay.EquipmentLayout
import io.github.noxaur.dsmod.core.feature.hud.equipmentdisplay.EquipmentLook
import io.github.noxaur.dsmod.core.feature.hud.equipmentdisplay.EquipmentPiece
import io.github.noxaur.dsmod.core.feature.visuals.etherwarpoutline.EtherwarpOutline
import io.github.noxaur.dsmod.core.feature.visuals.camera.freelook.FreeLook
import io.github.noxaur.dsmod.core.feature.visuals.world.fullbright.Fullbright
import io.github.noxaur.dsmod.core.feature.visuals.world.hideexplosions.HideExplosions
import io.github.noxaur.dsmod.core.feature.hud.equipmentdisplay.PetLook
import io.github.noxaur.dsmod.core.feature.hud.equipmentdisplay.PetPosition
import io.github.noxaur.dsmod.core.feature.utilities.chat.profileviewer.ProfileViewer
import io.github.noxaur.dsmod.core.feature.hud.layout.hudbackground.HudBackground
import io.github.noxaur.dsmod.core.feature.inventory.protection.armorswapguard.ArmorSwapGuard
import io.github.noxaur.dsmod.core.feature.inventory.itemrarity.ItemRarity
import io.github.noxaur.dsmod.core.feature.menus.keepcursor.KeepCursor
import io.github.noxaur.dsmod.core.feature.menus.betterui.menutabs.MenuTabs
import io.github.noxaur.dsmod.core.feature.inventory.protection.slotlock.SlotLock
import io.github.noxaur.dsmod.core.feature.inventory.items.raredropalert.RareDropAlert
import io.github.noxaur.dsmod.core.feature.combat.slayers.slayerbossalert.SlayerBossAlert
import io.github.noxaur.dsmod.core.feature.combat.slayers.slayertracker.SlayerTracker
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.hand.HandStyle
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.core.world.BoxStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun SettingsTree.assertOff(vararg types: Class<out HasFeature>) = types.forEach { assertFalse(feature(it).feature.enabled, "${it.simpleName} loads off") }

class ConfigFileTest {
    private val dir = Files.createTempDirectory("dsmod-config")
    private val path = dir.resolve("config.json")

    /** @keep `config/moulconfig.json` was written by version 0.1.0, which saved through MoulConfig's Gson. */
    @Test
    fun `a file the MoulConfig build wrote loads every value`() {
        path.writeText(resource("config/moulconfig.json"))
        val c = ConfigFile(path).load()
        val tree = SettingsTree(c)
        assertEquals(listOf("general.fullbright", "loot.lootAlerts", "hud.scale", "end.editBeam", "general.resetAnimations"), c.favorites)
        assertEquals(listOf("fullbright", "rare-drop-alert", "hudScale", "beamSections", "resetAnimations"), SettingsTree.favorites(c.favorites))

        assertFalse(tree.feature<HideCooldownMessages>().feature.enabled)
        assertFalse(tree.feature<CopyChat>().feature.enabled)

        tree.assertOff(ZealotTracker::class.java, EyeGuard::class.java, DragonTimer::class.java, FireballAlert::class.java, ProtectorStatus::class.java, BossFightTracker::class.java)
        tree.feature<ProtectorSpawnBeam>().run {
            assertFalse(feature.enabled)
            assertEquals(listOf(Section(20, 60), Section(150, 200)), sections.value)
            assertTrue(inFight.value)
        }

        assertFalse(tree.feature<DragSimOnly>().feature.enabled)
        c.general.run {
            assertEquals(344, settingsKey.value)
            assertEquals(71, favoriteKey.value)
        }
        assertTrue(tree.feature<Fullbright>().feature.enabled && tree.feature<HideExplosions>().feature.enabled)
        tree.feature<Animations>().run {
            assertFalse(feature.enabled)
            assertEquals(HandStyle.V1_7, style.value, "Exhibition, a block-hit, loads as 1.7")
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
            assertEquals(SlotStyle(MarkStyle.FRAME), inventoryStyle.value)
            assertEquals(SlotStyle(MarkStyle.FRAME), hotbarStyle.value)
            assertFalse(hotbarMatches.value || hotbarMarks.value)
            assertTrue(markCommon.value)
        }
        tree.assertOff(KeepCursor::class.java)
        tree.feature<SlotLock>().run {
            assertFalse(feature.enabled)
            assertEquals(74, slotLockKey.value)
            assertEquals(3, itemLockKey.value)
        }
        assertTrue(tree.feature<ArmorSwapGuard>().feature.enabled)

        assertFalse(tree.features.single { it.id == "better-ui" }.switch.value || tree.feature<MenuTabs>().feature.switch.value)
        assertEquals(
            listOf(MenuTab(TabEdge.TOP, 2, "bazaar", "minecraft:gold_ingot", "Bazaar"), MenuTab(TabEdge.BOTTOM, 6, item = "", tooltip = "Search", search = true)),
            tree.feature<MenuTabs>().tabs.value,
        )

        tree.feature<RareDropAlert>().run {
            assertFalse(feature.enabled)
            assertEquals(RareDropAlert.LootTier.RNGESUS, least.value)
        }
        tree.assertOff(SlayerTracker::class.java, SlayerBossAlert::class.java)

        c.hud.run {
            assertEquals(mapOf("zealot-tracker" to DsModConfig.Position(0.25f, 0.5f, 1.5f), "dragon-timer" to DsModConfig.Position(0.75f, 0.125f, 0.75f)), positions.value)
            assertEquals(Theme.VANILLA, theme.value)
            assertEquals(1.75f, scale.value)
        }
        tree.assertOff(HudBackground::class.java)

        assertTrue(tree.feature<ReportGameData>().feature.enabled)
    }

    /** @keep `config/main.json` was written by main at aebc7effe with every row changed from its default, on a Mac so
     * Ctrl Click has its row. Write it again only to add a setting, never to change a key that shipped. */
    @Test
    fun `a file this version's main wrote loads every value, and saves again unchanged`() {
        val written = resource("config/main.json")
        path.writeText(written)
        val c = ConfigFile(path).load()
        val rows = SettingsTree(c, mac = true).sections.flatMap { it.walk() }.filterIsInstance<Valued<*>>()
        assertEquals(emptyList(), rows.filter { it.value == it.default }.map { it.id }, "rows that loaded their default")
        assertEquals(Json.parseToJsonElement(written), Json.parseToJsonElement(ConfigFile.encode(c)))
    }

    @Test
    fun `what is saved loads again, the custom look too`() {
        val c = DsModConfig()
        val tree = SettingsTree(c)
        tree.feature<Camera>().distance.value = 9f
        tree.feature<ItemRarity>().inventoryStyle.value = SlotStyle(MarkStyle.AURA, opacity = 45, sparkles = true)
        c.hud.positions.value += "boss-fight-tracker" to DsModConfig.Position(0.5f, 0.5f, 2f)
        ConfigFile(path).save(c)
        val back = ConfigFile(path).load()
        val loaded = SettingsTree(back)
        assertEquals(ConfigFile.encode(c), ConfigFile.encode(back))
        assertEquals(9f, loaded.feature<Camera>().distance.value)
        assertEquals(SlotStyle(MarkStyle.AURA, opacity = 45, sparkles = true), loaded.feature<ItemRarity>().inventoryStyle.value)
        assertFalse(dir.resolve("config.json.tmp").exists(), "the temporary file is renamed over the old one")
    }

    @Test
    fun `an older rarity preset or look carries over as the nearest style, and the next save drops its keys`() {
        val looks = mapOf(
            """"rarityPreset":"SHOWCASE"""" to MarkStyle.AURA,
            """"rarityPreset":"GILDED"""" to MarkStyle.FRAME,
            """"rarityPreset":"MINIMAL"""" to MarkStyle.UNDERGLOW,
            """"rarityPreset":"CUSTOM","inventoryStyle":{"border":"NONE","fill":"HALO"}""" to MarkStyle.AURA,
            """"rarityPreset":"CUSTOM","inventoryStyle":{"border":"ROUNDED","fill":"NONE"}""" to MarkStyle.AURA,
            """"rarityPreset":"CUSTOM","inventoryStyle":{"border":"FLAT","fill":"NONE"}""" to MarkStyle.FRAME,
            """"rarityPreset":"CUSTOM","inventoryStyle":{"border":"NONE","fill":"WASH"}""" to MarkStyle.FRAME,
            """"rarityPreset":"CUSTOM","inventoryStyle":{"border":"NONE","fill":"NONE","floor":"SPARKS","gem":true}""" to MarkStyle.UNDERGLOW,
        )
        for ((old, mark) in looks) {
            path.writeText("""{"inventory":{$old,"hotbarMatches":false,"hotbarStyle":{"border":"NONE","fill":"NONE","floor":"PEDESTAL"}}}""")
            val c = ConfigFile(path).load()
            val rarity = SettingsTree(c).feature<ItemRarity>()
            assertEquals(SlotStyle(mark), rarity.inventoryStyle.value, old)
            assertEquals(SlotStyle(MarkStyle.UNDERGLOW), rarity.hotbarStyle.value, old)
            rarity.inventoryStyle.value = SlotStyle(MarkStyle.UNDERGLOW, opacity = 50)
            assertEquals(SlotStyle(MarkStyle.UNDERGLOW, opacity = 50), SettingsTree(c).feature<ItemRarity>().inventoryStyle.value, old)
            ConfigFile(path).save(c)
            val saved = path.readText()
            for (key in listOf("rarityPreset", "inventoryStyle", "hotbarStyle")) assertFalse("\"$key\"" in saved, "$old left $key in the file")
        }
    }

    @Test
    fun `Auto Left Click and Auto Right Click load as the parts of Auto Click, and the next save drops their keys`() {
        val old = """"autoLeftClickKey":74,"autoLeftClickMode":"TOGGLE","autoRightClickKey":75,"autoRightClickMode":"HOLD""""
        for ((switches, on) in listOf((false to true) to true, (true to false) to true, (false to false) to false)) {
            path.writeText("""{"general":{"autoLeftClick":${switches.first},"autoRightClick":${switches.second},$old}}""")
            val c = ConfigFile(path).load()
            SettingsTree(c).feature<AutoClick>().run {
                assertEquals(on, feature.enabled, "$switches")
                assertEquals(switches, left.switch.value to right.switch.value)
                assertEquals(listOf(74, 75), listOf(left.key.value, right.key.value))
                assertEquals(KeyMode.TOGGLE to KeyMode.HOLD, left.mode.value to right.mode.value)
                feature.enabled = false
            }
            ConfigFile(path).save(c)
            val saved = path.readText()
            for (key in listOf("autoLeftClick", "autoRightClick")) assertFalse("\"$key" in saved, "$switches left $key in the file")
            assertFalse(SettingsTree(ConfigFile(path).load()).feature<AutoClick>().feature.enabled, "$switches turned on again")
        }
    }

    @Test
    fun `a save on another system keeps a setting only a Mac shows`() {
        path.writeText("""{"general":{"ctrlClick":false}}""")
        val c = ConfigFile(path).load()
        SettingsTree(c, mac = false)
        ConfigFile(path).save(c)
        assertFalse(SettingsTree(ConfigFile(path).load(), mac = true).feature<CtrlClick>().feature.enabled)
    }

    @Test
    fun `a missing file, key or enum value takes its default, and an unknown key is passed over`() {
        assertEquals(ConfigFile.encode(DsModConfig()), ConfigFile.encode(ConfigFile(path).load()))
        path.writeText("""{"general":{"fullbright":true,"swingStyle":"GONE","removedSetting":3},"gone":{}}""")
        val file = ConfigFile(path)
        val tree = SettingsTree(file.load())
        assertTrue(tree.feature<Fullbright>().feature.enabled)
        assertEquals(HandStyle.MODERN, tree.feature<Animations>().style.value)
        assertEquals(SettingsTree().feature<ProtectorSpawnBeam>().sections.value, tree.feature<ProtectorSpawnBeam>().sections.value)
        assertNull(file.unreadable)
    }

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
        file.save(c)
        assertEquals("{not json", kept.readText())
        assertTrue(path.exists())
    }
}
