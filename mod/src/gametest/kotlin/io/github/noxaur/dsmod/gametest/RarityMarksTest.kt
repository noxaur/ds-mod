package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.Floor
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.RarityStyleScreen
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitStepper
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import org.lwjgl.glfw.GLFW

/**
 * Gives the player items the way dragsim.co builds them, checks the `Item` the edge reads from them, then opens
 * the inventory and a chest and checks the marks. Read the screenshots: the default gilded frame must sit around
 * the slot of each item but the Common one, and the tooltip must sit over a frame. Then the hotbar, and the editor.
 */
class RarityMarksTest : FabricClientGameTest {
    private fun marks(context: ClientGameTestContext) = context.computeOnClient<Map<Int, SlotMark>, Nothing> {
        DsModClient.game.readouts.single { it.feature.id == "rarity-marks" }.slots
    }

    private fun marked(vararg slots: Pair<Int, Role>) = slots.associate { (slot, role) -> slot to SlotMark(role, rarity = true) }

    private fun chestItem(name: String, rarity: String): ItemStack = ItemStack(Items.DIAMOND_SWORD).also {
        it.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        it.set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { put("item_data", CompoundTag().apply { putString("rarity", rarity) }) }))
    }

    /** Puts the cursor over the middle of a slot, in window pixels. */
    private fun hover(context: ClientGameTestContext, slot: Int) {
        val (x, y) = context.computeOnClient<Pair<Double, Double>, Nothing> { client ->
            val screen = client.gui.screen() as AbstractContainerScreen<*>
            val corner = screen as ContainerScreenAccessor
            val at = screen.menu.slots[slot]
            val scale = client.window.guiScale.toDouble()
            (corner.leftPos + at.x + 8) * scale to (corner.topPos + at.y + 8) * scale
        }
        context.input.setCursorPos(x, y)
        context.waitTicks(5)
    }

    private fun <T : AbstractWidget> widget(context: ClientGameTestContext, type: Class<T>, name: String) = context.computeOnClient<T, Nothing> { client ->
        // A stepper names itself with its value, as "Border: Gilded".
        client.gui.screen()!!.children().filterIsInstance(type).first { it.message.string == name || it.message.string.startsWith("$name: ") }
    }

    private fun click(context: ClientGameTestContext, widget: AbstractWidget, dx: Int = widget.width / 2) {
        val scale = context.computeOnClient<Double, Nothing> { it.window.guiScale.toDouble() }
        context.input.setCursorPos((widget.x + dx) * scale, (widget.y + widget.height / 2) * scale)
        context.input.pressMouse(0)
        context.waitTicks(2)
    }

    /**
     * Opens the style editor and works each kind of control in it with the mouse and the keyboard. Read the
     * screenshots: the inventory with its panel on the left, the preview on the right, and the hotbar's panel beside
     * the real hotbar, in each theme and with the hotbar unlinked.
     */
    private fun editor(context: ClientGameTestContext) {
        val settings = DsModClient.settings.inventory
        context.setScreen { RarityStyleScreen(settings, null) {} }
        context.waitTicks(5)
        context.takeScreenshot("rarity-style")
        // The test window is 427 GUI pixels wide, too narrow for the preview, so it is shown at 640 too.
        val window = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.screenWidth to it.window.screenHeight }
        val scale = context.computeOnClient<Int, Nothing> { it.options.guiScale().get() }
        context.input.resizeWindow(1280, 720)
        context.runOnClient<Nothing> { client ->
            client.options.guiScale().set(2)
            client.resizeGui()
        }
        context.waitTicks(5)
        context.takeScreenshot("rarity-style-wide")
        for (preset in RarityPreset.entries - RarityPreset.CUSTOM) {
            context.runOnClient<Nothing> { settings.rarityPreset = preset }
            context.waitTicks(2)
            context.takeScreenshot("rarity-preset-${preset.name.lowercase()}")
        }
        context.runOnClient<Nothing> { settings.rarityPreset = RarityPreset.GILDED }
        context.input.resizeWindow(window.first, window.second)
        context.runOnClient<Nothing> { client ->
            client.options.guiScale().set(scale)
            client.resizeGui()
        }
        context.waitTicks(5)

        // A change in the editor starts from the preset and makes it Custom.
        val border = widget(context, KitStepper::class.java, "Border")
        click(context, border, border.width - 4)
        check(settings.rarityPreset == RarityPreset.CUSTOM && settings.inventoryStyle == SlotStyle(border = Border.NONE)) {
            "The Border stepper's plus left ${settings.rarityPreset} with ${settings.inventoryStyle}"
        }
        context.runOnClient<Nothing> { it.gui.screen()!!.setFocused(border) }
        context.input.pressKey(GLFW.GLFW_KEY_LEFT)
        context.waitTicks(2)
        check(settings.inventoryStyle == SlotStyle()) { "Left on the Border stepper left ${settings.inventoryStyle}" }

        click(context, widget(context, KitCheckbox::class.java, "Gem"), 5)
        check(settings.inventoryStyle.gem) { "The Gem checkbox did not turn the gem on" }

        click(context, widget(context, KitCheckbox::class.java, "Match Inventory"), 5)
        check(!settings.hotbarMatches && settings.hotbarStyle == settings.inventoryStyle) { "Unlinking the hotbar left ${settings.hotbarStyle}" }
        // Two of each control now: the inventory's first, then the hotbar's.
        val floors = context.computeOnClient<List<KitStepper>, Nothing> { client ->
            client.gui.screen()!!.children().filterIsInstance<KitStepper>().filter { it.message.string.startsWith("Floor") }
        }
        check(floors.size == 2) { "The hotbar has no style of its own: ${floors.size} Floor steppers" }
        click(context, floors[1], floors[1].width - 4)
        check(settings.hotbarStyle.floor == Floor.PEDESTAL && settings.inventoryStyle.floor == Floor.NONE) {
            "The hotbar's Floor set ${settings.hotbarStyle.floor}, and the inventory's is ${settings.inventoryStyle.floor}"
        }
        context.waitTicks(5)
        context.takeScreenshot("rarity-style-hotbar")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
        context.waitTicks(2)
        context.takeScreenshot("rarity-style-vanilla")
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() == null }) { "Escape did not go back to the screen it opened over" }
        context.runOnClient<Nothing> {
            settings.rarityPreset = RarityPreset.GILDED
            settings.inventoryStyle = SlotStyle()
            settings.hotbarMatches = true
            settings.markCommon = false
        }
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

            // Slot 36 is the first hotbar slot, which `container.0` fills with a Common item, unmarked until Mark Common is on.
            val expected = marked(9 to Role.LEGENDARY, 10 to Role.RARE)
            check(marks(context) == expected) { "Marked ${marks(context)}" }

            context.setScreen { InventoryScreen(Minecraft.getInstance().player!!) }
            context.waitTicks(5)
            hover(context, 10)
            context.takeScreenshot("rarity-inventory")

            world.server.runOnServer<Nothing> { server ->
                val chest: Container = SimpleContainer(54).also {
                    it.setItem(0, chestItem("Common Sword", "COMMON"))
                    it.setItem(2, chestItem("Uncommon Sword", "UNCOMMON"))
                    it.setItem(4, chestItem("Rare Sword", "RARE"))
                    it.setItem(6, chestItem("Epic Sword", "EPIC"))
                    it.setItem(8, chestItem("Legendary Sword", "LEGENDARY"))
                    it.setItem(19, chestItem("Mythic Sword", "MYTHIC"))
                    it.setItem(21, chestItem("Divine Sword", "DIVINE"))
                    it.setItem(23, chestItem("Special Sword", "SPECIAL"))
                    it.setItem(25, ItemStack(Items.STONE))
                }
                server.playerList.players.first().openMenu(
                    SimpleMenuProvider({ id, inventory, _ -> ChestMenu.sixRows(id, inventory, chest) }, Component.literal("Test Chest")),
                )
            }
            context.waitFor { it.gui.screen() is AbstractContainerScreen<*> && it.gui.screen() !is InventoryScreen }
            context.waitTicks(10)
            val title = context.computeOnClient<String?, Nothing> { DsModClient.game.container.title?.plain }
            check(title == "Test Chest") { "The container was titled $title" }
            val chestMarks = marks(context)
            // The chest's 54 slots, then the player's 27 and 9 that it shows.
            val inChest = marked(
                2 to Role.UNCOMMON, 4 to Role.RARE, 6 to Role.EPIC, 8 to Role.LEGENDARY, 19 to Role.MYTHIC,
                21 to Role.DIVINE, 23 to Role.SPECIAL, 54 + 0 to Role.LEGENDARY, 54 + 1 to Role.RARE,
            )
            check(chestMarks == inChest) { "Marked $chestMarks" }
            hover(context, 4)
            context.takeScreenshot("rarity-chest")

            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.waitTicks(5)
            val back = context.computeOnClient<Int, Nothing> { DsModClient.game.container.id }
            check(back == 0 && marks(context) == expected) { "Back in the inventory, container $back is marked ${marks(context)}" }

            // With no menu open the hotbar carries the marks. Read the screenshot: white, blue and gold behind the first three items.
            context.runOnClient<Nothing> { DsModClient.settings.inventory.markCommon = true }
            world.server.runCommand("""item replace entity @a container.1 with bow[custom_data={item_data:{rarity:"RARE"}}]""")
            world.server.runCommand("""item replace entity @a container.2 with diamond_sword[custom_data={item_data:{rarity:"LEGENDARY"}}]""")
            context.waitTicks(10)
            check(marks(context)[36] == SlotMark(Role.COMMON, rarity = true)) { "Mark Common did not mark the dirt: ${marks(context)}" }
            check(marks(context)[38] == SlotMark(Role.LEGENDARY, rarity = true)) { "The hotbar is marked ${marks(context)}" }
            context.takeScreenshot("rarity-hotbar")

            editor(context)
        }
    }
}
