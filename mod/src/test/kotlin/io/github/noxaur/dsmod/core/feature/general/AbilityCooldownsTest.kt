package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.ownInventory
import io.github.noxaur.dsmod.harness.chat
import io.github.noxaur.dsmod.harness.text
import io.github.noxaur.dsmod.core.container.SlotCooldown
import io.github.noxaur.dsmod.core.feature.Alert
import io.github.noxaur.dsmod.core.game.Abilities
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.Hit
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerTime
import io.github.noxaur.dsmod.core.game.SetCharge
import io.github.noxaur.dsmod.core.game.SlotChanged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The signals no capture holds yet, from what DragSim Utils reads: the Tuba's `HOWL!`, a katana's `Soulcry!` line, the
 * drill's `You used`, and Riftwalker's charged hit. The action bar and the refusal are proven by the replay.
 */
class AbilityCooldownsTest : Harness(alone = AbilityCooldowns::class) {
    private val feature = tree.feature<AbilityCooldowns>()

    /** An item as dragsim.co writes one: the heading's name, the server's glyphs, then the button. */
    private fun weapon(name: String, ability: String, cooldown: String) = Item(
        "minecraft:diamond_sword",
        1,
        text(name),
        listOf(
            text("Damage: +100"),
            text(""),
            text(ServerSpan(ability), ServerSpan(""), ServerSpan("RIGHT CLICK")),
            text("Does something."),
            text("Mana Cost: 100"),
            text("Cooldown: $cooldown"),
            text(""),
            text("LEGENDARY SWORD"),
        ),
    )

    private fun riftwalker(piece: String, worn: Int) = Item(
        "minecraft:leather_$piece",
        1,
        text("Renowned Riftwalker ${piece.replaceFirstChar(Char::uppercase)}"),
        listOf(
            text("Tiered Bonus: Between Worlds ($worn/4)"),
            text("(2) Take 20% less damage from Endermen."),
            text("(4) Every 8s your next hit blinks you"),
            text("behind the target and deals double"),
            text(""),
        ),
    )

    @Test
    fun `the lore gives each ability its cooldown under its heading`() {
        val dawnbreaker = Item(
            "minecraft:netherite_sword",
            1,
            lore = listOf(
                text(ServerSpan("Radiant Chains"), ServerSpan(""), ServerSpan("RIGHT CLICK")),
                text("Mana Cost: 100"),
                text("Cooldown: 7s"),
                text(""),
                text(ServerSpan("Oathbound Charge"), ServerSpan(""), ServerSpan("OFFHAND KEY")),
                text("Cooldown: 4.4s"),
                text(""),
                text("Passive: Sanctuary Ring"),
                text("Strike Cooldown: 2s"),
                text("Item Ability: Eye RIGHT CLICK"),
                text("Cooldown: 5 seconds"),
            ),
        )
        assertEquals(
            listOf(
                io.github.noxaur.dsmod.core.game.Ability("Radiant Chains", 7000),
                io.github.noxaur.dsmod.core.game.Ability("Oathbound Charge", 4400),
                io.github.noxaur.dsmod.core.game.Ability("Eye", 5000),
            ),
            Abilities.of(dawnbreaker),
        )
        assertEquals(SetCharge("Between Worlds", 4, 4, 8000), Abilities.charge(riftwalker("boots", 4)))
        assertEquals(4350, Abilities.refusalMillis("This ability is on cooldown for 4.35s"))
        assertEquals("Giant's Slam", Abilities.cast("1,501/1,501❤     -100 Mana (Giant's Slam)     ✎ Mana 2,893/3,143"))
    }

    @Test
    fun `the Tuba's howl starts its timer once with its action bar part`() {
        post(*ownInventory(36 to weapon("Weird Tuba", "Howl", "20s")))
        post(chat("HOWL! You and nearby players gain +20 Strength."))
        pass(100)
        post(ActionBar(text("1,000/1,000❤     -150 Mana (Howl)     ✎ Mana 500/650")))
        assertEquals(listOf("Howl: 20s"), rows())
        assertEquals(SlotCooldown(time - 100, time + 19_900), readout().slots.getValue(36).cooldown)
        pass(19_900)
        assertEquals(listOf("Howl: Ready"), rows())
        pass(2_000)
        assertNull(readout().hud)
        assertTrue(readout().slots.isEmpty())
    }

    @Test
    fun `a katana's Soulcry starts it, and a player typing the line does not`() {
        post(*ownInventory(37 to weapon("Vorpal Katana", "Soulcry", "4s")))
        post(chat("[VIP] Someone: ☠ Soulcry! +250 ⫽ Ferocity against Endermen for 4s."))
        assertNull(readout().hud)
        post(chat("☠ Soulcry! +250 ⫽ Ferocity against Endermen for 4s."))
        assertEquals(listOf("Soulcry: 4.0s"), rows())
    }

    @Test
    fun `the drill's ability takes its length from the lore that names it`() {
        val drill = Item(
            "minecraft:prismarine_shard",
            1,
            text("Void Drill"),
            listOf(text("Selected Ability: Void Infusion"), text("Cooldown: 108s"), text("Fuel: 3,000/3,000")),
        )
        post(*ownInventory(38 to drill))
        post(chat("You used Void Infusion!"))
        assertEquals(listOf("Void Infusion: 1m 48s"), rows())
        assertEquals(setOf(38), readout().slots.keys)
        // An upgrade rewrites the drill's lore, and the next use follows it.
        pass(110_000)
        post(SlotChanged(38, drill.copy(lore = listOf(text("Selected Ability: Void Infusion"), text("Cooldown: 60s")))))
        post(chat("You used Void Infusion!"))
        assertEquals(listOf("Void Infusion: 1m 0s"), rows())
    }

    @Test
    fun `an ability no carried item names is not timed`() {
        post(*ownInventory(36 to weapon("Weird Tuba", "Howl", "20s")))
        post(ActionBar(text("1,000/1,000❤     -100 Mana (Giant's Slam)     ✎ Mana 500/650")))
        assertNull(readout().hud)
    }

    @Test
    fun `a refusal puts the timer where the server has it, and learns a length the lore leaves out`() {
        post(*ownInventory(36 to weapon("Giant's Sword", "Giant's Slam", "10s")))
        post(ActionBar(text("-100 Mana (Giant's Slam)")))
        pass(1_000)
        // The server's cooldown is 8s, as under a Sky Mall cut: 7s are left, not 9.
        post(chat("This ability is on cooldown for 7s"))
        assertEquals(listOf("Giant's Slam: 7.0s"), rows())
        pass(8_000)
        post(ActionBar(text("1,000/1,000❤")))
        post(ActionBar(text("-100 Mana (Giant's Slam)")))
        assertEquals(listOf("Giant's Slam: 8.0s"), rows())
    }

    @Test
    fun `Riftwalker's hit charges for 8 seconds and a hit while charging does not restart it`() {
        post(
            *ownInventory(
                5 to Item("minecraft:player_head", 1, text("Warden Helmet"), listOf(text("Chameleon"))),
                6 to riftwalker("chestplate", 0),
                7 to riftwalker("leggings", 0),
                8 to riftwalker("boots", 0),
            ),
        )
        post(Hit("minecraft:enderman"))
        assertEquals(listOf("Between Worlds: 8.0s"), rows())
        assertEquals(setOf(6, 7, 8), readout().slots.keys)
        pass(3_000)
        post(Hit("minecraft:enderman"))
        assertEquals(listOf("Between Worlds: 5.0s"), rows())
    }

    @Test
    fun `armor taken off while the feature was off no longer charges`() {
        val set = ownInventory(5 to riftwalker("helmet", 4), 6 to riftwalker("chestplate", 4), 7 to riftwalker("leggings", 4), 8 to riftwalker("boots", 4))
        post(*set)
        post(Hit("minecraft:enderman"))
        // Past the charge and the two seconds its Ready stays on the list.
        pass(11_000)
        feature.feature.enabled = false
        pass(1)
        // The container state follows the slots while the feature is off, and the feature sees none of it.
        post(SlotChanged(5, null), SlotChanged(6, null), SlotChanged(7, null), SlotChanged(8, null))
        feature.feature.enabled = true
        pass(1)
        post(Hit("minecraft:enderman"))
        assertNull(readout().hud)
    }

    @Test
    fun `three Riftwalker pieces do not charge`() {
        post(*ownInventory(6 to riftwalker("chestplate", 3), 7 to riftwalker("leggings", 3), 8 to riftwalker("boots", 3)))
        post(Hit("minecraft:enderman"))
        assertNull(readout().hud)
    }

    private var tick = 0L
    private var rate = 20L

    private fun serverSecond(ticks: Long) {
        pass(1_000)
        tick += ticks
        post(ServerTime(tick))
    }

    private fun castGiantsSlam() {
        post(*ownInventory(36 to weapon("Giant's Sword", "Giant's Slam", "10s")))
        post(ServerTime(tick))
        serverSecond(rate)
        post(ActionBar(text("-100 Mana (Giant's Slam)")))
    }

    @Test
    fun `a lagging server does not end the cooldown by the wall clock`() {
        rate = 10
        castGiantsSlam()
        assertEquals(listOf("Giant's Slam: 20s"), rows())
        repeat(10) { serverSecond(10) }
        assertEquals(listOf("Giant's Slam: 10s"), rows())
        assertEquals(SlotCooldown(time - 10_000, time + 10_000), readout().slots.getValue(36).cooldown)
        repeat(9) { serverSecond(10) }
        assertEquals(listOf("Giant's Slam: 1.0s"), rows())
        serverSecond(10)
        assertEquals(listOf("Giant's Slam: Ready"), rows())
    }

    @Test
    fun `between the server's time updates the timer counts down smoothly`() {
        rate = 20
        castGiantsSlam()
        assertEquals(listOf("Giant's Slam: 10s"), rows())
        pass(500)
        assertEquals(listOf("Giant's Slam: 9.5s"), rows())
        pass(500)
        post(ServerTime(tick + rate))
        assertEquals(listOf("Giant's Slam: 9.0s"), rows())
    }

    @Test
    fun `a refusal puts the timer on the server's count while it lags`() {
        rate = 10
        castGiantsSlam()
        serverSecond(10)
        post(chat("This ability is on cooldown for 7s"))
        assertEquals(listOf("Giant's Slam: 14s"), rows())
    }

    @Test
    fun `a long cooldown alerts once when it is over, when the player asked`() {
        feature.readyAlert.value = true
        post(*ownInventory(36 to weapon("Weird Tuba", "Howl", "20s"), 37 to weapon("Giant's Sword", "Giant's Slam", "5s")))
        post(ActionBar(text("-150 Mana (Howl)")))
        post(ActionBar(text("-100 Mana (Giant's Slam)")))
        pass(20_000)
        post(ActionBar(text("1,000/1,000❤")))
        post(ActionBar(text("1,000/1,000❤")))
        assertEquals(listOf("Howl Ready!"), game.messages.filterIsInstance<Alert>().map { it.title.plain })
    }
}
