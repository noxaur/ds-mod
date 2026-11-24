package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.inventory.HotdPlanner
import io.github.noxaur.dsmod.core.heart.HeartBlock
import io.github.noxaur.dsmod.core.heart.Perks
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SidePanel
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitDropdown
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

/** @keep The panel is a [SidePanel], left of a /hotd menu built from a capture's items: the top view holds tiers 10 to 6, the
 * bottom view tiers 5 to 1 and the Core */
class HotdPlannerTest : FabricClientGameTest {
    private class Hotd(val tier: Int, val core: Int, val spent: Int, val ender: String, val void: String, val owned: Map<String, Int> = emptyMap())

    private fun perk(name: String, max: Int, level: Int): Pair<Item, List<String>> {
        val ability = Perks.byName.getValue(name).ability
        if (level == 0) return (if (ability) Items.COAL_BLOCK else Items.COAL) to listOf("Level 1/$max", "", "Cost", "1 Token of the Dragon", "", "Click to unlock!")
        return Items.EMERALD to listOf("Level $level/$max", "", "Cost", "27 Ender Powder", "", "Click to upgrade!")
    }

    private fun fill(chest: SimpleContainer, hotd: Hotd, top: Int) {
        for (row in 0 until 5) {
            val tier = top - row
            chest.setItem(row * 9, ServerMenus.lored(Items.STAINED_GLASS_PANE.lime(), "Tier $tier", listOf("You have unlocked this tier.", "", "UNLOCKED")))
            for (p in Perks.all.filter { it.tier == tier }) {
                val (item, lore) = perk(p.name, p.max, hotd.owned[p.name] ?: 0)
                chest.setItem(row * 9 + p.column, ServerMenus.lored(item, p.name, lore))
            }
            if (tier == 5) chest.setItem(row * 9 + 4, ServerMenus.lored(Items.NETHER_STAR, "Core of the Dragon", listOf("Level ${hotd.core}")))
        }
        if (top < 10) chest.setItem(8, ServerMenus.lored(Items.ARROW, "Scroll Up", listOf("Show the tiers above.")))
        if (top > 5) chest.setItem(44, ServerMenus.lored(Items.ARROW, "Scroll Down", listOf("Show the tiers below.")))
        chest.setItem(49, ServerMenus.lored(Items.DRAGON_EGG, "Heart of the Dragon", listOf(
            "Tier: ${hotd.tier}", "", "Token of the Dragon: ${20 - hotd.spent} (${hotd.spent} spent)", "",
            "Ender Powder: ${hotd.ender}", "  (+1 extra base powder)", "Void Powder: ${hotd.void}", "  (+2 extra base powder)",
        )))
    }

    private fun open(context: ClientGameTestContext, world: TestSingleplayerContext, hotd: Hotd, top: Int) =
        ServerMenus.open(context, world, 6, "Heart of the Dragon", goBack = false) { fill(it, hotd, top) }

    private fun marks(context: ClientGameTestContext): Map<Int, SlotMark> =
        context.computeOnClient<Map<Int, SlotMark>, Nothing> { DsModClient.game.readouts.single { it.feature.id == "hotd-planner" }.slots }

    private fun next(context: ClientGameTestContext): Pair<Int, SlotMark>? {
        context.waitTicks(10)
        return marks(context).entries.singleOrNull { !it.value.dim }?.toPair()
    }

    override fun runTest(context: ClientGameTestContext) {
        val feature = DsModClient.feature<HotdPlanner>()
        context.runOnClient<Nothing> {
            feature.block.value = HeartBlock.END_STONE
            feature.items.value = true
            feature.powder.value = true
            feature.pets.value = false
        }
        context.worldBuilder().create().use { world ->
            val reset = Hotd(10, 10, 0, "1,334,335", "1,073,221")
            open(context, world, reset, 10)
            val unseen = next(context)
            check(unseen?.first == 44 && unseen.second.role == Role.GOOD) { "The lower tiers were never shown, but the mark is $unseen, not on Scroll Down" }
            check(marks(context).filterValues { it.dim }.keys == setOf(0, 9, 18, 27, 36)) { "The tier panes were not grayed out: ${marks(context)}" }
            context.takeScreenshot("hotd-planner-unseen")

            open(context, world, reset, 5)
            open(context, world, reset, 10)
            val top = next(context)
            check(top != null && top.first != 44 && top.second.role == Role.GOOD) { "With every tier seen, the mark is $top, not on a perk" }
            context.takeScreenshot("hotd-planner-charcoal")

            val block = ServerMenus.widget<KitDropdown<*>>(context) { it.message.string.startsWith("Block") }!!
            context.click(block, settle = 2)
            check(context.computeOnClient<Boolean, Nothing> { block.menu != null }) { "A press on Block did not open its menu" }
            context.takeScreenshot("hotd-planner-dropdown")
            context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
            context.waitTicks(2)
            context.checkClient("Escape closed /hotd and not the menu") { it.gui.screen() is ContainerScreen && block.menu == null }
            context.click(block, settle = 2)
            context.input.pressKey(GLFW.GLFW_KEY_DOWN)
            context.input.pressKey(GLFW.GLFW_KEY_DOWN)
            context.input.pressKey(GLFW.GLFW_KEY_DOWN)
            context.input.pressKey(GLFW.GLFW_KEY_ENTER)
            context.waitTicks(2)
            check(context.computeOnClient<HeartBlock, Nothing> { feature.block.value } == HeartBlock.AMETHYST) { "Down three times and Enter did not pick Amethyst" }
            check(ServerMenus.widget<KitCheckbox>(context) { it.visible && it.message.string == "Pristine" } != null) { "Pristine did not show for Amethyst" }
            val pets = ServerMenus.widget<KitCheckbox>(context) { it.message.string == "Pets" }!!
            context.click(pets, settle = 2)
            check(context.computeOnClient<Boolean, Nothing> { feature.pets.value }) { "A press on Pets did not tick it" }
            check(next(context)?.second?.role == Role.GOOD) { "Amethyst with Pets planned nothing" }
            context.takeScreenshot("hotd-planner-amethyst")
            context.click(pets, settle = 2)
            context.click(block, button = 1, settle = 2)
            check(context.computeOnClient<HeartBlock, Nothing> { feature.block.value } == HeartBlock.END_STONE) { "A right press did not step Amethyst on to End Stone" }

            // @keep Tier 1 with no Core gives one token, and Mining Speed spent it, so with no powder its next level is out of reach
            // @keep Obsidian, since the default speed already mines End Stone at once and Mining Speed would add nothing
            context.runOnClient<Nothing> { feature.block.value = HeartBlock.OBSIDIAN }
            val broke = Hotd(1, 0, 1, "0", "0", mapOf("Mining Speed" to 1))
            open(context, world, broke, 5)
            val short = next(context)
            check(short != null && short.second.role == Role.BAD) { "With no powder, the mark is $short, not red" }
            check(context.computeOnClient<Boolean, Nothing> { feature.purse?.let { it.enderShort } == true }) { "Ender was not short" }
            context.takeScreenshot("hotd-planner-short")
            context.runOnClient<Nothing> { feature.block.value = HeartBlock.END_STONE }

            context.inTheme(Theme.VANILLA) {
                open(context, world, reset, 10)
                next(context)
                context.takeScreenshot("hotd-planner-vanilla")
            }
            context.runOnClient<Nothing> { it.player!!.closeContainer() }
        }
    }
}
