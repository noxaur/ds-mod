package io.github.noxaur.dsmod.core.hand

import io.github.noxaur.dsmod.core.hand.Etherwarp.Miss
import io.github.noxaur.dsmod.core.hand.Etherwarp.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EtherwarpTest {
    // @keep An Aspect of the Void's lore as a capture shows it, with the glyphs of the server's tooltip font left out.
    private val aspect = listOf(
        "Instant Transmission RIGHT CLICK",
        "Teleport 15 blocks ahead of you and",
        "gain +50 ✦Speed for 3 seconds.",
        "Transmission Tuners: 3/3",
        "",
        "Etherwarp SNEAK RIGHT CLICK",
        "Teleport to the block you are",
        "looking at, up to 57 blocks away.",
        "Mana Cost: 250",
        "",
        "EPIC SWORD",
    )

    private val conduit = listOf(
        "Combine with an Aspect of the End",
        "or Aspect of the Void in an anvil",
        "to unlock Etherwarp.",
        "",
        "Dropped by the Voidgloom Seraph IV.",
        "",
        "LEGENDARY",
    )

    @Test
    fun `the range is read from the Etherwarp section, not from Instant Transmission's above it`() {
        assertEquals(57, Etherwarp.range(aspect))
        assertEquals(0, Etherwarp.range(aspect.take(5)))
    }

    @Test
    fun `an item that only names Etherwarp, as the Conduit does, has no range`() {
        assertEquals(0, Etherwarp.range(conduit))
        assertEquals(0, Etherwarp.range(conduit + listOf("Teleport up to 9 blocks away.")))
        assertEquals(0, Etherwarp.range(emptyList()))
    }

    private fun world(vararg solid: String) = Etherwarp.Blocks { x, y, z -> "$x,$y,$z" in solid }

    private fun east(blocks: Etherwarp.Blocks, range: Int = 57, beyond: Int = 0) =
        Etherwarp.target(0.5, 1.5, 0.5, 1.0, 0.0, 0.0, range, beyond, blocks)

    @Test
    fun `the line stops at the first block with a collision box`() {
        assertEquals(Target(10, 1, 0, null), east(world("10,1,0", "20,1,0")))
    }

    @Test
    fun `a block with no room above it fails, one or two blocks up`() {
        assertEquals(Target(10, 1, 0, Miss.NO_ROOM), east(world("10,1,0", "10,2,0")))
        assertEquals(Target(10, 1, 0, Miss.NO_ROOM), east(world("10,1,0", "10,3,0")))
        assertEquals(Target(10, 1, 0, null), east(world("10,1,0", "10,4,0")))
    }

    @Test
    fun `a block whose near side is within the range is reached, and one past it is too far`() {
        assertEquals(Target(57, 1, 0, null), east(world("57,1,0")))
        assertNull(east(world("58,1,0")))
        assertEquals(Target(58, 1, 0, Miss.TOO_FAR), east(world("58,1,0"), beyond = Etherwarp.BEYOND))
        assertNull(east(world("200,1,0"), beyond = Etherwarp.BEYOND))
    }

    @Test
    fun `the line crosses every block it passes through, corners and negative sides included`() {
        val look = Triple(-0.8, -0.6, 0.0)
        val found = Etherwarp.target(0.5, 1.5, 0.5, look.first, look.second, look.third, 57, 0, world("-1,1,0", "0,0,0"))
        assertEquals(Target(-1, 1, 0, null), found)
        assertEquals(Target(0, -4, 0, null), Etherwarp.target(0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 57, 0, world("0,-4,0")))
    }

    @Test
    fun `a line that meets nothing boxes nothing`() {
        assertNull(east(world()))
    }
}
