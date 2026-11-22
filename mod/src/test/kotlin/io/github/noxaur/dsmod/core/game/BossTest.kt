package io.github.noxaur.dsmod.core.game

import kotlin.test.Test
import io.github.noxaur.dsmod.harness.text
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BossTest {
    private fun seen(entity: String, name: String) = EntitySeen(1, "minecraft:$entity", text(name))

    @Test
    fun `every dragon is a Dragon, since its name holds only its level and health`() {
        assertEquals(Boss(1, BossKind.DRAGON, "Dragon"), Boss.parse(seen("ender_dragon", "[Lv100]  9M/9M")))
    }

    @Test
    fun `the Protector is the iron golem that names it`() {
        assertEquals(Boss(1, BossKind.PROTECTOR, "Endstone Protector"), Boss.parse(seen("iron_golem", "[Lv100] Endstone Protector 9.9M/10M")))
        assertNull(Boss.parse(seen("iron_golem", "[Lv5] Village Guard 100/100")))
    }

    @Test
    fun `a slayer boss is named up to its health, whatever the fight adds after the hearts`() {
        assertEquals(Boss(1, BossKind.SLAYER, "Revenant Horror I"), Boss.parse(seen("zombie", "☠ Revenant Horror I 1.5M/3.8M❤")))
        assertEquals(Boss(1, BossKind.SLAYER, "Voidgloom Seraph IV"), Boss.parse(seen("enderman", "☠ Voidgloom Seraph IV 2.5B/2.5B❤ 100 Hits")))
        assertNull(Boss.parse(seen("zombie", "☠ Revenant Horror I")))
    }

    @Test
    fun `a hologram or a player is never a boss, whatever its name says`() {
        assertNull(Boss.parse(seen("armor_stand", "☠ Revenant Horror I 1.5M/3.8M❤")))
        assertNull(Boss.parse(seen("player", "☠ Revenant Horror I 1.5M/3.8M❤")))
    }

    @Test
    fun `every boss passes the filters the tracker runs before it reads a name`() {
        for (boss in listOf(seen("ender_dragon", ""), seen("iron_golem", "[Lv100] Endstone Protector 10M/10M"), seen("zombie", "☠ Revenant Horror I 1.5M/3.8M❤"))) {
            assertEquals(true, Boss.FILTERS.any { it.matches(boss) }, "$boss")
        }
    }
}
