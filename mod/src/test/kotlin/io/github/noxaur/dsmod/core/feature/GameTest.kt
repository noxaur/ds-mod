package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.chat
import io.github.noxaur.dsmod.harness.feature
import io.github.noxaur.dsmod.harness.text
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.boss.BossBeacon
import io.github.noxaur.dsmod.core.feature.boss.BossTracer
import io.github.noxaur.dsmod.core.feature.end.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.EyeGuard
import io.github.noxaur.dsmod.core.feature.end.ProtectorStatus
import io.github.noxaur.dsmod.core.feature.general.DragSimOnly
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Boss
import io.github.noxaur.dsmod.core.game.EntityGone
import io.github.noxaur.dsmod.core.game.EntitySeen
import io.github.noxaur.dsmod.core.game.Pet
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.EntityBeam
import io.github.noxaur.dsmod.core.world.Tracer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class GameTest : Harness() {
    private val config = tree.config

    private inner class SidebarListener {
        var heard = 0
        val feature = Feature(config, "listener", Area.ANYWHERE, Store.END, "Hears the sidebar in a test.").apply {
            on<Sidebar> { _, _ -> heard++ }
        }
    }

    private fun marks(feature: HasFeature) = readout(feature).marks

    private val protector = EntitySeen(36339, "minecraft:iron_golem", text("[Lv100] Endstone Protector 10M/10M"))
    private val pet = EntitySeen(12, "minecraft:armor_stand", text("[Lvl 100] Black Cat"))

    @Test
    fun `the tracker reads only what a feature that is on reads, and every feature's while a capture records`() {
        tree.feature<BossBeacon>().feature.enabled = false
        assertEquals(listOf(Pet.STANDS), game.entityFilters())
        tree.feature<BossBeacon>().feature.enabled = true
        assertEquals(listOf(Pet.STANDS) + Boss.FILTERS, game.entityFilters())
        assertSame(game.entityFilters(), game.entityFilters())
        tree.features.forEach { it.enabled = false }
        assertEquals(emptyList(), game.entityFilters())
        game.tap = { _, _ -> }
        assertEquals(listOf(Pet.STANDS) + Boss.FILTERS, game.entityFilters())
    }

    @Test
    fun `a feature marks the entities it reads until they are gone`() {
        tree.feature<BossBeacon>().feature.enabled = true
        game.post(0, pet)
        game.post(0, protector)
        game.tick(0)
        assertEquals(listOf(EntityBeam(36339, "Endstone Protector", Role.BAD)), marks(tree.feature<BossBeacon>()))
        game.post(50, EntityGone(36339))
        game.tick(50)
        assertEquals(emptyList(), marks(tree.feature<BossBeacon>()))
    }

    @Test
    fun `a feature switched on hears the entities the client already holds`() {
        tree.feature<BossBeacon>().feature.enabled = true
        game.post(0, protector)
        game.tick(0)
        tree.feature<BossTracer>().feature.enabled = true
        game.tick(50)
        assertEquals(listOf(Tracer(36339, Role.BAD)), marks(tree.feature<BossTracer>()))
    }

    @Test
    fun `a feature switched on between two ticks hears an entity that comes then once`() {
        var heard = 0
        val listener = Feature(config, "listener", Area.ANYWHERE, Store.END, "Hears entities in a test.").apply {
            readEntities(*Boss.FILTERS.toTypedArray())
            on<EntitySeen> { _, _ -> heard++ }
        }
        val game = Game(listOf(listener))
        listener.enabled = false
        game.tick(0)
        listener.enabled = true
        game.post(1, protector)
        game.tick(50)
        assertEquals(1, heard)
    }

    @Test
    fun `a feature's world state starts over on a hop and its session state when the player leaves`() {
        val listener = object : HasFeature {
            override val feature = Feature(config, "listener", Area.ANYWHERE, Store.END, "Keeps state in a test.")
            var inWorld by feature.world { 0 }
            var inSession by feature.session { 0 }

            init {
                feature.on<SystemMessage> { _, _ ->
                    inWorld++
                    inSession++
                }
            }
        }
        val game = Game(listOf(listener.feature))
        val line = chat("Anything")
        game.post(0, line)
        game.post(1, WorldChanged)
        assertEquals(0 to 1, listener.inWorld to listener.inSession)

        game.post(2, line)
        listener.feature.enabled = false
        game.tick(3)
        assertEquals(0 to 2, listener.inWorld to listener.inSession)

        game.post(4, LeftServer)
        listener.feature.enabled = true
        game.tick(5)
        assertEquals(0 to 0, listener.inWorld to listener.inSession)
    }

    @Test
    fun `a part handles the events it needs beside its feature, in the order each was added`() {
        val heard = ArrayList<String>()
        val listener = object : HasFeature {
            override val feature = Feature(config, "listener", Area.ANYWHERE, Store.END, "Hears a hop in a test.")

            init {
                feature.on<WorldChanged> { _, _ -> heard += "part" }
                feature.on<WorldChanged> { _, _ -> heard += "feature" }
            }
        }
        val game = Game(listOf(listener.feature))
        game.post(0, WorldChanged)
        assertEquals(listOf("part", "feature"), heard)
    }

    @Test
    fun `a slayer spawn line in the world the player left claims no boss in the next`() {
        tree.feature<BossBeacon>().feature.enabled = true
        say(0, "☠ SLAYER BOSS SPAWNING!")
        game.post(1, WorldChanged)
        game.post(2, EntitySeen(7, "minecraft:zombie", text("☠ Revenant Horror I 1.5M/3.8M❤")))
        game.tick(2)
        assertEquals(emptyList(), marks(tree.feature<BossBeacon>()))
    }

    private val revenant = EntitySeen(7, "minecraft:zombie", text("☠ Revenant Horror I 1.5M/3.8M❤"))

    private fun ownMarks() = marks(tree.feature<BossBeacon>()).map { (it as EntityBeam).entity } to marks(tree.feature<BossTracer>()).map { (it as Tracer).entity }

    @Test
    fun `Boss Beacon switched on after the spawn line marks the boss Boss Tracer took for the player's`() {
        tree.feature<BossTracer>().feature.enabled = true
        say(0, "☠ SLAYER BOSS SPAWNING!")
        game.post(1, revenant)
        tree.feature<BossBeacon>().feature.enabled = true
        game.tick(2)
        assertEquals(listOf(7) to listOf(7), ownMarks())
    }

    @Test
    fun `switching Boss Beacon off or back on after the player left keeps Boss Tracer's claim`() {
        tree.feature<BossTracer>().feature.enabled = true
        game.post(0, LeftServer)
        say(1, "☠ SLAYER BOSS SPAWNING!")
        game.post(2, revenant)
        tree.feature<BossBeacon>().feature.enabled = true
        game.tick(3)
        assertEquals(listOf(7) to listOf(7), ownMarks())
        tree.feature<BossBeacon>().feature.enabled = false
        game.tick(4)
        assertEquals(emptyList<Int>() to listOf(7), ownMarks())
    }

    @Test
    fun `a slayer boss already in view when Boss Tracer comes on is not taken for the player's`() {
        tree.feature<BossBeacon>().feature.enabled = true
        game.post(0, EntitySeen(9, "minecraft:zombie", text("☠ Atoned Horror V 50M/50M❤")))
        say(1_000, "☠ SLAYER BOSS SPAWNING!")
        tree.feature<BossTracer>().feature.enabled = true
        game.tick(2_000)
        game.post(2_500, revenant)
        game.tick(2_500)
        assertEquals(listOf(7) to listOf(7), ownMarks())
    }

    @Test
    fun `a dragon that leaves leaves no egg timer behind`() {
        game.post(0, TabList(listOf(text("Area: The End"))))
        say(0, "The Dragon Egg is respawning in 30 seconds.")
        say(60_000, "☠ The Young Dragon has spawned!")
        say(61_000, "☠ The Young Dragon has de-spawned.")
        game.tick(61_000)
        assertNull(hud(tree.feature<DragonTimer>()))
    }

    @Test
    fun `a new world forgets every entity, since it numbers them again`() {
        tree.feature<BossBeacon>().feature.enabled = true
        game.post(0, protector)
        game.post(0, WorldChanged)
        game.tick(0)
        assertEquals(emptyList(), marks(tree.feature<BossBeacon>()))
        tree.feature<BossTracer>().feature.enabled = true
        game.tick(50)
        assertEquals(emptyList(), marks(tree.feature<BossTracer>()))
    }

    @Test
    fun `a feature that was switched off comes back knowing nothing of the world it left`() {
        game.post(0, TabList(listOf(text("Area: The End"))))
        say(0, "☠ The Young Dragon has spawned!")
        game.tick(0)
        assertNotNull(hud(tree.feature<DragonTimer>()))

        tree.feature<DragonTimer>().feature.enabled = false
        game.tick(1_000)
        assertNull(hud(tree.feature<DragonTimer>()))

        tree.feature<DragonTimer>().feature.enabled = true
        game.tick(2_000)
        assertNull(hud(tree.feature<DragonTimer>()))
    }

    @Test
    fun `a feature that is switched back on hears the tab list again`() {
        game.post(0, TabList(listOf("Area: The End", "Protector: Rising (Tier 4/5, 20)").map { text(it) }))
        game.tick(0)
        val before = assertNotNull(hud(tree.feature<ProtectorStatus>()))

        tree.feature<ProtectorStatus>().feature.enabled = false
        game.tick(1_000)
        assertNull(hud(tree.feature<ProtectorStatus>()))

        tree.feature<ProtectorStatus>().feature.enabled = true
        game.tick(2_000)
        assertEquals(before, hud(tree.feature<ProtectorStatus>()))
    }

    @Test
    fun `a feature locked to an area hears nothing before the server names one`() {
        say(0, "☠ The Young Dragon has spawned!")
        game.tick(0)
        assertNull(hud(tree.feature<DragonTimer>()))
    }

    @Test
    fun `an edge feature locked to an area runs only there, and stays on for the HUD`() {
        val eyes = tree.feature<EyeGuard>().feature
        assertEquals(false to true, eyes.runs to eyes.on)
        game.post(0, TabList(listOf(text("Area: The End"))))
        assertEquals(true, eyes.runs)
        game.post(1, WorldChanged)
        assertEquals(false, eyes.runs)
    }

    @Test
    fun `a feature that is switched back on hears the sidebar again`() {
        val listener = SidebarListener()
        val game = Game(listOf(listener.feature))
        game.post(0, Sidebar(lines = listOf(text("⏣ Village"))))
        listener.feature.enabled = false
        game.tick(1_000)
        listener.feature.enabled = true
        game.tick(2_000)
        assertEquals(2, listener.heard)
    }

    @Test
    fun `a feature locked to an area hears the player leave the server from anywhere`() {
        var heard = 0
        val listener = Feature(config, "listener", Area.END, Store.END, "Hears the player leave in a test.").apply {
            on<LeftServer> { _, _ -> heard++ }
        }
        Game(listOf(listener)).post(0, LeftServer)
        assertEquals(1, heard)
    }

    @Test
    fun `a feature switched on after the player left the server hears none of its sidebar`() {
        val listener = SidebarListener()
        val game = Game(listOf(listener.feature))
        listener.feature.enabled = false
        game.tick(0)
        game.post(1, Sidebar(lines = listOf(text("⏣ Village"))))
        game.post(2, LeftServer)
        listener.feature.enabled = true
        game.tick(3)
        assertEquals(0, listener.heard)
    }

    @Test
    fun `the area of the world the player left does not outlive the hop`() {
        game.post(0, TabList(listOf(text("Area: The End"))))
        game.post(1, WorldChanged)
        say(2, "☠ The Young Dragon has spawned!")
        game.tick(2)
        assertNull(hud(tree.feature<DragonTimer>()))
    }

    @Test
    fun `off dragsim co no feature hears a line, hides it or shows a readout`() {
        val offline = object : Harness(SettingsTree(config), onDragSim = { false }) {}
        val game = offline.game
        val tab = TabList(listOf("Area: The End", "Protector: Rising (Tier 4/5, 20)").map { text(it) })
        val refusal = chat("This ability is on cooldown for 4.35s")
        game.post(0, tab)
        game.tick(0)
        assertEquals(false, game.wants(SystemMessage::class.java))
        assertEquals(true, game.post(0, refusal))
        assertEquals(emptyList(), game.readouts.mapNotNull { it.hud })

        offline.tree.feature<DragSimOnly>().feature.enabled = false
        offline.pass(1_000)
        assertNotNull(offline.hud(offline.tree.feature<ProtectorStatus>()))
        assertEquals(false, game.post(1_000, refusal))
    }

    @Test
    fun `a feature on by default tells the player once where its switch is`() {
        say(0, "This ability is on cooldown for 4.35s")
        say(1, "This ability is on cooldown for 4.30s")
        assertEquals(listOf("Hid a message from the server. Turn off Hide Cooldown Messages under Chat in /dsmod to see them."), game.messages.map { it.detail.plain })
        assertEquals(setOf("hide-cooldown-messages"), game.hidden.value.told)
    }

    @Test
    fun `a feature the player turned on hides without telling them`() {
        val hider = Feature(config, "hider", Area.ANYWHERE, Store.GENERAL, "Hides every line in a test.", default = false).apply {
            on<SystemMessage> { _, _ -> hide() }
        }
        hider.enabled = true
        val game = Game(listOf(hider))
        assertEquals(false, game.post(0, chat("Anything")))
        assertEquals(emptyList(), game.messages)
    }
}
