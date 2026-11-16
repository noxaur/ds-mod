package io.github.noxaur.dsmod.edge

import com.google.common.collect.MapMaker
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
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Util
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * Slot Lock. Every way a menu moves an item goes through one method of the screen, so `SlotClickMixin` asks
 * [refuses] there, before the game predicts the move or sends anything. The drop key outside a menu goes another
 * way, which `DropKeyMixin` covers. The server can still change a locked slot itself.
 */
object SlotGuards {
    private const val FLASH_MILLIS = 300L
    private const val WORN_FIRST = 36

    private lateinit var feature: SlotLock
    private lateinit var settings: DsModConfig.Inventory

    private val on: Boolean get() = DsModClient.runs<SlotLock>()
    private val locks: SlotLocks get() = feature.locks

    /** The slot the key went down over, while [pressing]. */
    private var from: Int? = null
    private var pressing = false
    private var itemKeyDown = false
    /** While the item key is held, whether the items it passes over lock or unlock, as the first one with an ID did. */
    private var lockingItems: Boolean? = null

    /** Each item's ID by its data component, which stacks share and never change. Reading one copies the whole tag. */
    private val ids: MutableMap<CustomData, String> = MapMaker().weakKeys().makeMap()

    /** The ID the server gives an item that does not stack, in `item_data.uuid`, or null. */
    private fun id(stack: ItemStack): String? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        return ids.getOrPut(data) { data.copyTag().getCompoundOrEmpty("item_data").getStringOr("uuid", "") }.ifEmpty { null }
    }

    /** Whether [slot] is a locked slot of the player's inventory, or holds a locked item there. */
    private fun locked(slot: Slot): Boolean {
        val own = own(slot) ?: return false
        return own in locks.locked || locks.items.isNotEmpty() && id(slot.item) in locks.items
    }
    private var refused: Slot? = null
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
            itemKeyDown = false
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                // A key typed into a field, such as an anvil's, is text.
                if (!on || screen.focused is EditBox) return@register true
                // A held key repeats, and a repeat is not a press.
                when (event.key()) {
                    settings.slotLockKey.takeUnless(Keys::isMouse) -> if (!pressing) press(screen)
                    settings.itemLockKey.takeUnless(Keys::isMouse) -> if (!itemKeyDown) pressItem(screen)
                    else -> return@register true
                }
                false
            }
            ScreenKeyboardEvents.afterKeyRelease(screen).register { _, event ->
                if (event.key() == settings.slotLockKey) release(client, screen)
                if (event.key() == settings.itemLockKey) itemKeyDown = false
            }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                if (!on) return@register true
                when (event.button()) {
                    settings.slotLockKey.takeIf(Keys::isMouse) -> press(screen)
                    settings.itemLockKey.takeIf(Keys::isMouse) -> pressItem(screen)
                    else -> return@register true
                }
                false
            }
            ScreenMouseEvents.allowMouseRelease(screen).register { _, event ->
                when {
                    pressing && Keys.isMouse(settings.slotLockKey) && event.button() == settings.slotLockKey -> release(client, screen)
                    itemKeyDown && Keys.isMouse(settings.itemLockKey) && event.button() == settings.itemLockKey -> itemKeyDown = false
                    else -> return@register true
                }
                false
            }
            ScreenEvents.afterForeground(screen).register { _, graphics, _, _, _ -> if (on) {
                    // Each frame, so a quick sweep along a row misses no item.
                    if (itemKeyDown) lockItem(screen, first = false)
                    placed(screen, graphics) {
                        padlocks(screen, graphics)
                        link(screen, graphics)
                    }
                } }
            ScreenEvents.remove(screen).register {
                pressing = false
                itemKeyDown = false
                refused = null
            }
        }
    }

    private fun press(screen: AbstractContainerScreen<*>) {
        pressing = true
        from = own((screen as ContainerScreenAccessor).hoveredSlot)
    }

    private fun pressItem(screen: AbstractContainerScreen<*>) {
        itemKeyDown = true
        lockingItems = null
        lockItem(screen, first = true)
    }

    /**
     * Locks the item under the mouse wherever it goes, or unlocks it. The first item the held key meets decides
     * which, and every item after it gets the same. An item that stacks has no ID of its own to lock.
     */
    private fun lockItem(screen: AbstractContainerScreen<*>, first: Boolean) {
        val slot = (screen as ContainerScreenAccessor).hoveredSlot ?: return
        if (own(slot) == null || !slot.hasItem()) return
        val id = id(slot.item)
        if (id == null) {
            // Only where the key went down, since a sweep crosses stacks on its way.
            if (first) Chat.notice(feature.title, text { bad("An item that stacks cannot be locked. Lock its slot instead.") })
            return
        }
        val lock = lockingItems ?: (id !in locks.items).also { lockingItems = it }
        if ((id in locks.items) != lock) feature.locks = locks.item(id, lock)
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
        val client = Minecraft.getInstance()
        val own = own(slot)
        val partner = own?.let(locks::partner)
        if (input == ContainerInput.QUICK_MOVE && partner != null) {
            val above = find(screen, maxOf(own, partner))
            // The click the number key sends over a slot, which the player asked for by linking the two.
            if (above != null) client.gameMode?.handleContainerInput(screen.menu.containerId, above.index, minOf(own, partner), ContainerInput.SWAP, client.player!!)
            return true
        }
        val refuses = slot != null && locked(slot) ||
            // A number key or the off-hand key over another slot trades with that hotbar slot or the off hand.
            input == ContainerInput.SWAP && (button in locks.locked || client.player?.inventory?.getItem(button)?.let(::id) in locks.items) ||
            // A double click gathers every stack like the carried one, out of locked slots too.
            input == ContainerInput.PICKUP_ALL && screen.menu.slots.any { locked(it) && ItemStack.isSameItemSameComponents(screen.menu.carried, it.item) }
        if (refuses) {
            refused = slot ?: screen.menu.slots.firstOrNull { own(it) == button }
            refusedAt = Util.getMillis()
            return true
        }
        return input == ContainerInput.QUICK_MOVE && slot != null && slot.hasItem() && steered(client, screen, slot)
    }

    /**
     * A Shift click moves an item to wherever the menu has room, which may be a locked slot that is empty or holds
     * more of the same. When it may, the item is carried to a slot that is not locked instead, with the two clicks
     * the player would make by hand, or stays where it is when there is none. Returns whether it took the click over.
     */
    private fun steered(client: Minecraft, screen: AbstractContainerScreen<*>, slot: Slot): Boolean {
        val stack = slot.item
        val own = own(slot)
        val main = screen.menu.slots.filter { own(it) in 0..SlotLocks.ABOVE.last && it.isActive }
        val into = when {
            // Out of a menu the game fills the inventory from its last slot back.
            own == null -> main.reversed()
            // Out of the inventory into another menu, which has no locks.
            screen.menu !is InventoryMenu -> return false
            else -> {
                // Armor goes on when its slot is free, then the hotbar and the rows above trade with each other.
                val worn = client.player!!.getEquipmentSlotForItem(stack).takeIf { it.isArmor }?.let { find(screen, WORN_FIRST + it.index) }
                listOfNotNull(worn?.takeIf { own in 0..SlotLocks.ABOVE.last }) + main.filter { own > SlotLocks.ABOVE.last || (own(it) in SlotLocks.HOTBAR) != (own in SlotLocks.HOTBAR) }
            }
        }
        fun fits(to: Slot) = if (to.hasItem()) ItemStack.isSameItemSameComponents(stack, to.item) && to.item.count < to.getMaxStackSize(stack) else to.mayPlace(stack)
        if (into.none { locked(it) && fits(it) }) return false
        val free = into.filterNot(::locked)
        val to = free.firstOrNull { it.hasItem() && fits(it) && it.item.count + stack.count <= it.getMaxStackSize(stack) } ?: free.firstOrNull { !it.hasItem() && fits(it) }
        if (to != null && screen.menu.carried.isEmpty) {
            val player = client.player!!
            client.gameMode?.handleContainerInput(screen.menu.containerId, slot.index, 0, ContainerInput.PICKUP, player)
            client.gameMode?.handleContainerInput(screen.menu.containerId, to.index, 0, ContainerInput.PICKUP, player)
        }
        return true
    }

    /** Whether the drop key outside a menu must not drop the held item. */
    fun keepsHeld(): Boolean {
        val inventory = Minecraft.getInstance().player?.inventory ?: return false
        return on && (inventory.selectedSlot in locks.locked || id(inventory.selectedItem) in locks.items)
    }

    private inline fun placed(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor, draw: () -> Unit) {
        if (locks.locked.isEmpty() && locks.items.isEmpty() && !pressing) return
        screen as ContainerScreenAccessor
        graphics.pose().pushMatrix()
        graphics.pose().translate(screen.leftPos.toFloat(), screen.topPos.toFloat())
        draw()
        graphics.pose().popMatrix()
    }

    /**
     * The link of the slot under the mouse, or the one being made: a frame around both slots and a line between
     * them, over the items so no item hides it. Links at rest show only their padlocks.
     */
    private fun link(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor) {
        val over = own((screen as ContainerScreenAccessor).hoveredSlot) ?: return
        val from = from.takeIf { pressing }
        val other = if (from == null) locks.partner(over) else over.takeIf { it != from && locks.link(from, it) != null }
        val a = find(screen, from ?: over) ?: return
        val b = find(screen, other ?: return) ?: return
        val (upper, lower) = if (a.y <= b.y) a to b else b to a
        val color = Role.ACTION.argb()
        Kit.slotBorder(graphics, a.x, a.y, color)
        Kit.slotBorder(graphics, b.x, b.y, color)
        Kit.line(graphics, upper.x + 8, upper.y + 17, lower.x + 8, lower.y - 2, color)
    }

    private fun padlocks(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor) {
        val flash = (1f - (Util.getMillis() - refusedAt) / FLASH_MILLIS.toFloat()).coerceAtLeast(0f)
        for (slot in screen.menu.slots) {
            if (!slot.isActive || !locked(slot)) continue
            val own = own(slot)
            if (flash > 0f && slot === refused) Kit.flash(graphics, slot.x, slot.y, 16, 16, flash)
            // Yellow for a slot, blue for a linked one, and green for an item that takes its lock along.
            val role = if (own !in locks.locked) Role.GOOD else if (locks.partner(own!!) != null) Role.ACTION else Role.WARNING
            Kit.icon(graphics, Icon.LOCK, slot.x, slot.y, role.argb())
        }
    }
}
