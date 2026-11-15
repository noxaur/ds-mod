package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.inventory.SlotLocks
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.util.Util
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * Slot Lock. Every way a menu moves an item goes through one method of the screen, so `SlotClickMixin` asks
 * [refuses] there, before the game predicts the move or sends anything. The drop key outside a menu goes another
 * way, which `DropKeyMixin` covers. The server can still change a locked slot itself.
 */
object SlotGuards {
    private const val FLASH_MILLIS = 300L

    private lateinit var feature: SlotLock
    private lateinit var settings: DsModConfig.Inventory

    private val on: Boolean get() = DsModClient.active && settings.slotLock
    private val locks: SlotLocks get() = feature.locks

    /** The slot the key went down over, while [pressing]. */
    private var from: Int? = null
    private var pressing = false
    private var refused: Int? = null
    private var refusedAt = 0L

    /** The slot's index in the player's own inventory, or null for a slot of the menu's. */
    private fun own(slot: Slot?): Int? = if (slot != null && slot.container is Inventory) slot.containerSlot else null

    private fun find(screen: AbstractContainerScreen<*>, own: Int): Slot? = screen.menu.slots.firstOrNull { own(it) == own && it.isActive }

    fun register(feature: SlotLock, settings: DsModConfig.Inventory) {
        this.feature = feature
        this.settings = settings
        // Fabric drops a screen's listeners on every init, so they are added again here.
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            pressing = false
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                // A key typed into a field, such as an anvil's, is text.
                if (!on || screen.focused is EditBox || Keys.isMouse(settings.slotLockKey) || event.key() != settings.slotLockKey) return@register true
                // A held key repeats, and a repeat is not a press.
                if (!pressing) press(screen)
                false
            }
            ScreenKeyboardEvents.afterKeyRelease(screen).register { _, event -> if (event.key() == settings.slotLockKey) release(client, screen) }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                if (!on || !Keys.isMouse(settings.slotLockKey) || event.button() != settings.slotLockKey) return@register true
                press(screen)
                false
            }
            ScreenMouseEvents.allowMouseRelease(screen).register { _, event ->
                if (!pressing || !Keys.isMouse(settings.slotLockKey) || event.button() != settings.slotLockKey) return@register true
                release(client, screen)
                false
            }
            ScreenEvents.afterBackground(screen).register { _, graphics, _, _, _ -> if (on) placed(screen, graphics) { links(screen, graphics) } }
            ScreenEvents.afterForeground(screen).register { _, graphics, _, _, _ -> if (on) placed(screen, graphics) { padlocks(screen, graphics) } }
            ScreenEvents.remove(screen).register { pressing = false }
        }
    }

    private fun press(screen: AbstractContainerScreen<*>) {
        pressing = true
        from = own((screen as ContainerScreenAccessor).hoveredSlot)
    }

    /** A press and release over one slot locks or unlocks it, and a release over another slot links the two. */
    private fun release(client: Minecraft, screen: AbstractContainerScreen<*>) {
        if (!pressing) return
        pressing = false
        val from = from ?: return
        val to = own((screen as ContainerScreenAccessor).hoveredSlot) ?: return
        if (!on) return
        if (from == to) {
            feature.locks = locks.toggle(from)
            return
        }
        val linked = locks.link(from, to)
        if (linked == null) Chat.notice(feature.title, text { bad("Link a hotbar slot with a slot in the rows above it.") }) else feature.locks = linked
    }

    /** Whether the menu must not act on this click. A Shift click on a linked slot trades the pair's places instead. */
    fun refuses(screen: AbstractContainerScreen<*>, slot: Slot?, button: Int, input: ContainerInput): Boolean {
        if (!on) return false
        val own = own(slot)
        val partner = own?.let(locks::partner)
        if (input == ContainerInput.QUICK_MOVE && partner != null) {
            val client = Minecraft.getInstance()
            val above = find(screen, maxOf(own, partner))
            // The click the number key sends over a slot, which the player asked for by linking the two.
            if (above != null) client.gameMode?.handleContainerInput(screen.menu.containerId, above.index, minOf(own, partner), ContainerInput.SWAP, client.player!!) // rule:no-automation Slot Lock
            return true
        }
        val refuses = own in locks.locked ||
            // A number key or the off-hand key over another slot trades with that hotbar slot or the off hand.
            input == ContainerInput.SWAP && button in locks.locked ||
            // A double click gathers every stack like the carried one, out of locked slots too.
            input == ContainerInput.PICKUP_ALL && screen.menu.slots.any {
                own(it) in locks.locked && ItemStack.isSameItemSameComponents(screen.menu.carried, it.item)
            }
        if (refuses) {
            refused = own ?: button
            refusedAt = Util.getMillis()
        }
        return refuses
    }

    /** Whether the drop key outside a menu must not drop the held item. */
    fun keepsHeld(): Boolean = on && Minecraft.getInstance().player?.inventory?.selectedSlot in locks.locked

    private inline fun placed(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, draw: () -> Unit) {
        if (locks.locked.isEmpty() && !pressing) return
        screen as ContainerScreenAccessor
        graphics.pose().pushMatrix()
        graphics.pose().translate(screen.leftPos.toFloat(), screen.topPos.toFloat())
        draw()
        graphics.pose().popMatrix()
    }

    /** A line from each hotbar slot to its partner, behind the items, and one that follows the mouse while a link is made. */
    private fun links(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor) {
        val making = from?.takeIf { pressing }?.let { from ->
            own((screen as ContainerScreenAccessor).hoveredSlot)?.takeIf { it != from && locks.link(from, it) != null }?.let { minOf(from, it) to maxOf(from, it) }
        }
        for ((hotbar, above) in locks.links.toList() + listOfNotNull(making)) {
            val a = find(screen, above) ?: continue
            val b = find(screen, hotbar) ?: continue
            val (upper, lower) = if (a.y <= b.y) a to b else b to a
            Kit.line(graphics, upper.x + 8, upper.y + 16, lower.x + 8, lower.y - 1, Role.ACTION.argb())
        }
    }

    private fun padlocks(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor) {
        val flash = (1f - (Util.getMillis() - refusedAt) / FLASH_MILLIS.toFloat()).coerceAtLeast(0f)
        for (slot in screen.menu.slots) {
            val own = own(slot) ?: continue
            if (own !in locks.locked || !slot.isActive) continue
            if (flash > 0f && own == refused) Kit.flash(graphics, slot.x, slot.y, 16, 16, flash)
            Kit.icon(graphics, Icon.LOCK, slot.x, slot.y, (if (locks.partner(own) != null) Role.ACTION else Role.WARNING).argb())
        }
    }
}
