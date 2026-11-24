package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.inventory.itemrarity.ItemRarity
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ItemRarityTest : FabricClientGameTest {
    private fun marks(context: ClientGameTestContext) = context.computeOnClient<Map<Int, SlotMark>, Nothing> {
        DsModClient.game.readouts.single { it.feature.id == "item-rarity" }.slots
    }

    private fun marked(vararg slots: Pair<Int, Role>) = slots.associate { (slot, role) -> slot to SlotMark(role, rarity = true) }

    private fun hotbarLook(context: ClientGameTestContext) {
        val itemRarity = DsModClient.feature<ItemRarity>()
        val window = SettingsSteps.windowSize(context)
        val scale = context.computeOnClient<Int, Nothing> { it.options.guiScale().get() }
        SettingsSteps.fit(context, 2)
        val driver = SettingsDriver(context)
        context.setScreen { DsModClient.settingsScreen(null, SettingsTabs(SettingsSteps.at("Inventory")), reveal = "hotbarMatches") }
        context.waitTicks(2)
        val match = driver.spot("hotbarMatches")
        context.runOnClient<Nothing> { it.gui.toastManager().clear() }
        driver.move(600, 340)
        context.takeScreenshot("rarity-hotbar-look")
        driver.click(match.x + 20, match.y + match.height / 2)
        check(!itemRarity.hotbarMatches.value && itemRarity.hotbarStyle.value == itemRarity.containerStyle()) { "Match Inventory off left the hotbar ${itemRarity.hotbarStyle.value}" }
        val style = driver.spot("hotbarStyle")
        driver.move(style.controlX + 8, style.controlY + 8)
        context.input.scroll(-1.0)
        context.waitTicks(2)
        check(itemRarity.hotbarStyle.value.mark != itemRarity.containerStyle().mark) {
            "The hotbar's Style set ${itemRarity.hotbarStyle.value.mark}, and the inventory's is ${itemRarity.containerStyle().mark}"
        }
        context.shotInBothThemes("rarity-hotbar-own")
        context.setScreen { null }
        context.waitTicks(5)
        context.takeScreenshot("rarity-hotbar-in-game")
        context.runOnClient<Nothing> { itemRarity.hotbarMatches.value = true }
        check(itemRarity.hotbarLook() == itemRarity.containerStyle()) { "Matching left the hotbar in ${itemRarity.hotbarLook()}" }
        context.runOnClient<Nothing> {
            itemRarity.inventoryStyle.value = SlotStyle()
            itemRarity.hotbarStyle.value = SlotStyle()
            itemRarity.hotbarMatches.value = true
            itemRarity.markCommon.value = false
        }
        context.input.resizeWindow(window.first, window.second)
        SettingsSteps.guiScale(context, scale)
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            listOf(
                """item replace entity @a container.9 with diamond_sword[custom_name="§6Fiery Blade",lore=["§7First line",{text:"Second",color:"red"}],custom_data={item_data:{rarity:"LEGENDARY"},item_stats:{stats:{damage:5.0d}}}]""",
                """item replace entity @a container.10 with bow[custom_name={text:"Rare Bow",color:"blue",bold:true},custom_data={item_data:{rarity:"RARE"}}]""",
                """item replace entity @a container.11 with stone[custom_name="No rarity here"]""",
                """item replace entity @a container.0 with dirt[custom_data={item_data:{rarity:"COMMON"}}]""",
                """item replace entity @a container.12 with apple[custom_data={item_data:{rarity:"NEW_RARITY"}}]""",
            ).forEach(world.server::runCommand)
            context.waitTicks(10)

            val blade = context.computeOnClient<Item?, Nothing> { DsModClient.game.container.slots[9] }
            check(
                blade == Item(
                    "minecraft:diamond_sword",
                    1,
                    ServerText(ServerSpan("Fiery Blade", 0xFFAA00)),
                    listOf(ServerText(ServerSpan("First line", 0xAAAAAA)), ServerText(ServerSpan("Second", 0xFF5555))),
                    JsonObject(
                        mapOf(
                            "item_data" to JsonObject(mapOf("rarity" to JsonPrimitive("LEGENDARY"))),
                            "item_stats" to JsonObject(mapOf("stats" to JsonObject(mapOf("damage" to JsonPrimitive(5.0))))),
                        ),
                    ),
                ),
            ) { "The client read $blade" }
            val bow = context.computeOnClient<Item?, Nothing> { DsModClient.game.container.slots[10] }
            check(bow?.name == ServerText(ServerSpan("Rare Bow", 0x5555FF, bold = true))) { "The client read $bow" }

            val expected = marked(9 to Role.LEGENDARY, 10 to Role.RARE)
            check(marks(context) == expected) { "Marked ${marks(context)}" }

            context.openInventory()
            context.waitTicks(5)
            context.hoverSlot(10, settle = 5)
            context.takeScreenshot("rarity-inventory")

            RarityChest.open(context, world.server)
            val title = context.computeOnClient<String?, Nothing> { DsModClient.game.container.title?.plain }
            check(title == "Test Chest") { "The container was titled $title" }
            val chestMarks = marks(context)
            val inChest = marked(
                2 to Role.UNCOMMON, 4 to Role.RARE, 6 to Role.EPIC, 8 to Role.LEGENDARY, 19 to Role.MYTHIC,
                21 to Role.DIVINE, 23 to Role.SPECIAL, 54 + 0 to Role.LEGENDARY, 54 + 1 to Role.RARE,
            )
            check(chestMarks == inChest) { "Marked $chestMarks" }
            context.hoverSlot(4, settle = 5)
            context.takeScreenshot("rarity-chest")

            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.waitTicks(5)
            val back = context.computeOnClient<Int, Nothing> { DsModClient.game.container.id }
            check(back == 0 && marks(context) == expected) { "Back in the inventory, container $back is marked ${marks(context)}" }

            context.runOnClient<Nothing> { DsModClient.feature<ItemRarity>().markCommon.value = true }
            world.server.runCommand("""item replace entity @a container.1 with bow[custom_data={item_data:{rarity:"RARE"}}]""")
            world.server.runCommand("""item replace entity @a container.2 with diamond_sword[custom_data={item_data:{rarity:"LEGENDARY"}}]""")
            context.waitTicks(10)
            check(marks(context)[36] == SlotMark(Role.COMMON, rarity = true)) { "Mark Common did not mark the dirt: ${marks(context)}" }
            check(marks(context)[38] == SlotMark(Role.LEGENDARY, rarity = true)) { "The hotbar is marked ${marks(context)}" }
            context.takeScreenshot("rarity-hotbar")

            hotbarLook(context)
        }
    }
}
