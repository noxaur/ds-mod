package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.edge.ui.Atlas
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SlotArtsTest {
    @BeforeEach
    fun clear() = SlotArts.reset()

    @Test
    fun `asking for an atlas does not create or upload it`() {
        assertNull(SlotArts.atlas(DsModConfig().inventory, Surface.HOTBAR))
    }

    @Test
    fun `a restyle keeps the previous atlas until the tick uploads the next`() {
        var uploads = 0
        val paint = { _: Surface, _: SlotStyle, _: Boolean ->
            uploads++
            Atlas(Identifier.fromNamespaceAndPath("dsmod", "rarity/test-$uploads"), 1, 1)
        }
        val inventory = DsModConfig().inventory
        inventory.hotbarMatches = false
        assertNull(SlotArts.atlas(inventory, Surface.HOTBAR))
        assertEquals(0, uploads)

        SlotArts.tick(paint)
        assertEquals(1, uploads)
        val first = SlotArts.atlas(inventory, Surface.HOTBAR)
        checkNotNull(first)

        inventory.hotbarStyle = SlotStyle(border = Border.FLAT)
        val during = SlotArts.atlas(inventory, Surface.HOTBAR)
        assertEquals(first.id, during?.id)
        assertEquals(1, uploads)

        SlotArts.tick(paint)
        assertEquals(2, uploads)
        assertNotEquals(first.id, SlotArts.atlas(inventory, Surface.HOTBAR)?.id)
    }
}
