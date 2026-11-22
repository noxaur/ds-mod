package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.container.MarkStyle
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.edge.ui.Atlas
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SlotArtsTest {
    private var uploads = 0
    private val paint = { _: Surface, _: SlotStyle, _: Boolean ->
        uploads++
        Atlas(Identifier.fromNamespaceAndPath("dsmod", "rarity/test-$uploads"), 1, 1)
    }

    @BeforeEach
    fun clear() {
        SlotArts.wantedStyle.fill(null)
        SlotArts.ready.clear()
    }

    @Test
    fun `asking for an atlas does not create or upload it`() {
        assertNull(SlotArts.atlas(SettingsTree().feature<ItemRarity>(), Surface.HOTBAR))
    }

    @Test
    fun `a restyle keeps the previous atlas until the tick uploads the next`() {
        val inventory = SettingsTree().feature<ItemRarity>()
        inventory.hotbarMatches.value = false
        assertNull(SlotArts.atlas(inventory, Surface.HOTBAR))
        assertEquals(0, uploads)

        SlotArts.tick(paint)
        assertEquals(1, uploads)
        val first = SlotArts.atlas(inventory, Surface.HOTBAR)
        checkNotNull(first)

        inventory.hotbarStyle.value = SlotStyle(MarkStyle.AURA)
        val during = SlotArts.atlas(inventory, Surface.HOTBAR)
        assertEquals(first.id, during?.id)
        assertEquals(1, uploads)

        SlotArts.tick(paint)
        assertEquals(2, uploads)
        assertNotEquals(first.id, SlotArts.atlas(inventory, Surface.HOTBAR)?.id)
    }

    @Test
    fun `a change of opacity alone paints nothing, since the blit draws it`() {
        val inventory = SettingsTree().feature<ItemRarity>()
        SlotArts.atlas(inventory, Surface.CONTAINER)
        SlotArts.tick(paint)
        inventory.inventoryStyle.value = SlotStyle(opacity = 40)
        SlotArts.atlas(inventory, Surface.CONTAINER)
        SlotArts.tick(paint)
        assertEquals(1, uploads)
        inventory.inventoryStyle.value = SlotStyle(opacity = 40, sparkles = true)
        SlotArts.atlas(inventory, Surface.CONTAINER)
        SlotArts.tick(paint)
        assertEquals(2, uploads)
    }
}
