package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.container.SlotCooldown
import io.github.noxaur.dsmod.core.feature.Alert
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.Abilities
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Hit
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SetCharge
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SystemMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The signals no capture holds yet, from what DragSim Utils reads: the Tuba's `HOWL!`, a katana's `Soulcry!` line, the
 * drill's `You used`, and Riftwalker's charged hit. The action bar and the refusal are proven by the replay.
 */
class AbilityCooldownsTest {
    private val config = DsModConfig()
    private val game = Game(listOf(features(config).single { it.id == "ability-cooldowns" }))
    private var time = 1_000_000L

    private fun post(vararg events: GameEvent) {
        for (event in events) game.post(time, event)
        game.tick(time)
    }

    private fun at(millis: Long) {
        time += millis
        game.tick(time)
    }

    private fun line(plain: String) = ServerText(ServerSpan(plain))

    /** An item as dragsim.co writes one: the heading's name, the server's glyphs, then the button. */
    private fun weapon(name: String, ability: String, cooldown: String) = Item(
        "minecraft:diamond_sword",
        1,
        line(name),
        listOf(
            line("Damage: +100"),
            line(""),
            ServerText(ServerSpan(ability), ServerSpan(""), ServerSpan("RIGHT CLICK")),
            line("Does something."),
            line("Mana Cost: 100"),
            line("Cooldown: $cooldown"),
            line(""),
            line("LEGENDARY SWORD"),
        ),
    )

    private fun riftwalker(piece: String, worn: Int) = Item(
        "minecraft:leather_$piece",
        1,
        line("Renowned Riftwalker ${piece.replaceFirstChar(Char::uppercase)}"),
        listOf(
            line("Tiered Bonus: Between Worlds ($worn/4)"),
            line("(2) Take 20% less damage from Endermen."),
            line("(4) Every 8s your next hit blinks you"),
            line("behind the target and deals double"),
            line(""),
        ),
    )

    private fun inventory(vararg slots: Pair<Int, Item>) = arrayOf<GameEvent>(ContainerOpened(0, null, 46)) + slots.map { (slot, item) -> SlotChanged(slot, item) }

    private val readout get() = game.readouts.single()
    private val rows get() = readout.hud?.rows?.map { it.text.plain } ?: emptyList()

    @Test
    fun `the lore gives each ability its cooldown under its heading`() {
        val dawnbreaker = Item(
            "minecraft:netherite_sword",
            1,
            lore = listOf(
                ServerText(ServerSpan("Radiant Chains"), ServerSpan(""), ServerSpan("RIGHT CLICK")),
                line("Mana Cost: 100"),
                line("Cooldown: 7s"),
                line(""),
                ServerText(ServerSpan("Oathbound Charge"), ServerSpan(""), ServerSpan("OFFHAND KEY")),
                line("Cooldown: 4.4s"),
                line(""),
                line("Passive: Sanctuary Ring"),
                line("Strike Cooldown: 2s"),
                line("Item Ability: Eye RIGHT CLICK"),
                line("Cooldown: 5 seconds"),
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
        post(*inventory(36 to weapon("Weird Tuba", "Howl", "20s")))
        post(SystemMessage(line("HOWL! You and nearby players gain +20 Strength.")))
        at(100)
        post(ActionBar(line("1,000/1,000❤     -150 Mana (Howl)     ✎ Mana 500/650")))
        assertEquals(listOf("Howl: 20s"), rows)
        assertEquals(SlotCooldown(time - 100, time + 19_900), readout.slots.getValue(36).cooldown)
        at(19_900)
        assertEquals(listOf("Howl: Ready"), rows)
        at(2_000)
        assertNull(readout.hud)
        assertTrue(readout.slots.isEmpty())
    }

    @Test
    fun `a katana's Soulcry starts it, and a player typing the line does not`() {
        post(*inventory(37 to weapon("Vorpal Katana", "Soulcry", "4s")))
        post(SystemMessage(line("[VIP] Someone: ☠ Soulcry! +250 ⫽ Ferocity against Endermen for 4s.")))
        assertNull(readout.hud)
        post(SystemMessage(line("☠ Soulcry! +250 ⫽ Ferocity against Endermen for 4s.")))
        assertEquals(listOf("Soulcry: 4.0s"), rows)
    }

    @Test
    fun `the drill's ability takes its length from the lore that names it`() {
        val drill = Item(
            "minecraft:prismarine_shard",
            1,
            line("Void Drill"),
            listOf(line("Selected Ability: Void Infusion"), line("Cooldown: 108s"), line("Fuel: 3,000/3,000")),
        )
        post(*inventory(38 to drill))
        post(SystemMessage(line("You used Void Infusion!")))
        assertEquals(listOf("Void Infusion: 1m 48s"), rows)
    }

    @Test
    fun `an ability no carried item names is not timed`() {
        post(*inventory(36 to weapon("Weird Tuba", "Howl", "20s")))
        post(ActionBar(line("1,000/1,000❤     -100 Mana (Giant's Slam)     ✎ Mana 500/650")))
        assertNull(readout.hud)
    }

    @Test
    fun `a refusal puts the timer where the server has it, and learns a length the lore leaves out`() {
        post(*inventory(36 to weapon("Giant's Sword", "Giant's Slam", "10s")))
        post(ActionBar(line("-100 Mana (Giant's Slam)")))
        at(1_000)
        // The server's cooldown is 8s, as under a Sky Mall cut: 7s are left, not 9.
        post(SystemMessage(line("This ability is on cooldown for 7s")))
        assertEquals(listOf("Giant's Slam: 7.0s"), rows)
        at(8_000)
        post(ActionBar(line("1,000/1,000❤")))
        post(ActionBar(line("-100 Mana (Giant's Slam)")))
        assertEquals(listOf("Giant's Slam: 8.0s"), rows)
    }

    @Test
    fun `Riftwalker's hit charges for 8 seconds and a hit while charging does not restart it`() {
        post(
            *inventory(
                5 to Item("minecraft:player_head", 1, line("Warden Helmet"), listOf(line("Chameleon"))),
                6 to riftwalker("chestplate", 0),
                7 to riftwalker("leggings", 0),
                8 to riftwalker("boots", 0),
            ),
        )
        post(Hit("minecraft:enderman"))
        assertEquals(listOf("Between Worlds: 8.0s"), rows)
        assertEquals(setOf(6, 7, 8), readout.slots.keys)
        at(3_000)
        post(Hit("minecraft:enderman"))
        assertEquals(listOf("Between Worlds: 5.0s"), rows)
    }

    @Test
    fun `three Riftwalker pieces do not charge`() {
        post(*inventory(6 to riftwalker("chestplate", 3), 7 to riftwalker("leggings", 3), 8 to riftwalker("boots", 3)))
        post(Hit("minecraft:enderman"))
        assertNull(readout.hud)
    }

    @Test
    fun `a long cooldown alerts once when it is over, when the player asked`() {
        config.general.cooldownReadyAlert = true
        post(*inventory(36 to weapon("Weird Tuba", "Howl", "20s"), 37 to weapon("Giant's Sword", "Giant's Slam", "5s")))
        post(ActionBar(line("-150 Mana (Howl)")))
        post(ActionBar(line("-100 Mana (Giant's Slam)")))
        at(20_000)
        post(ActionBar(line("1,000/1,000❤")))
        post(ActionBar(line("1,000/1,000❤")))
        assertEquals(listOf("Howl Ready!"), game.messages.filterIsInstance<Alert>().map { it.title.plain })
    }
}
