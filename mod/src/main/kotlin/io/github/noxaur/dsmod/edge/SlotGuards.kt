package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.protection.slotlock.SlotLock
import io.github.noxaur.dsmod.core.inventory.ShiftSlot
import io.github.noxaur.dsmod.core.inventory.SlotLocks
import io.github.noxaur.dsmod.core.inventory.shiftPlaces
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.input.ContainerClicks
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.Refusals
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.util.Util
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/** @keep Every way a menu moves an item goes through one method of the screen. The drop key outside a menu goes another way. The server can still change a locked slot itself. */
object SlotGuards {
    private const val FLASH_MILLIS = 300L
    private const val RESULT_WAIT = 5

    private lateinit var feature: SlotLock

    fun bind(slotLock: SlotLock) {
        feature = slotLock
    }
    private val slotKey: Int get() = feature.slotLockKey.value
    private val itemKey: Int get() = feature.itemLockKey.value

    private val on: Boolean get() = feature.feature.runs
    private val locks: SlotLocks get() = feature.locks

    private var from: Int? = null
    private var pressing = false
    private var itemKeyDown = false
    private var lockingItems: Boolean? = null

    private fun locked(slot: Slot): Boolean {
        val own = own(slot) ?: return false
        return own in locks.locked || locks.items.isNotEmpty() && slot.item.itemUuid in locks.items
    }
    private var refused: Slot? = null
    private var refusedAt = 0L

    private fun own(slot: Slot?): Int? = if (slot != null && slot.container is Inventory) slot.containerSlot else null

    private fun find(screen: AbstractContainerScreen<*>, own: Int): Slot? = screen.menu.slots.firstOrNull { own(it) == own && it.isActive }

    private var pending: Pair<AbstractContainerScreen<*>, Int>? = null
    private var pendingWaits = 0

    @InputApproved(by = "noxaur", reaches = ["Refusals.allowKeyPress", "Refusals.allowMouseClick", "Refusals.allowMouseRelease"])
    fun register() {
        // @keep The server refills a crafting result after the click, so the next craft waits for that packet.
        Ticks.end { client ->
            val (screen, index) = pending ?: return@end
            if (!on || client.gui.screen() !== screen) {
                pending = null
                return@end
            }
            val slot = screen.menu.getSlot(index)
            if (!slot.hasItem()) {
                if (++pendingWaits > RESULT_WAIT) pending = null
                return@end
            }
            pendingWaits = 0
            if (!steered(client, screen, slot)) pending = null
        }
        ScreenHooks.onEveryLayout<AbstractContainerScreen<*>> { screen, _ ->
            val client = Minecraft.getInstance()
            pressing = false
            itemKeyDown = false
            Refusals.allowKeyPress(screen).register { _, event ->
                if (!on || screen.focused is EditBox) return@register true
                // @keep A held key repeats, and a repeat is not a press.
                when (event.key()) {
                    slotKey.takeUnless(Keys::isMouse) -> if (!pressing) press(screen)
                    itemKey.takeUnless(Keys::isMouse) -> if (!itemKeyDown) pressItem(screen)
                    else -> return@register true
                }
                false
            }
            ScreenKeyboardEvents.afterKeyRelease(screen).register { _, event ->
                if (event.key() == slotKey) release(client, screen)
                if (event.key() == itemKey) itemKeyDown = false
            }
            Refusals.allowMouseClick(screen).register { _, event ->
                if (!on) return@register true
                when (event.button()) {
                    slotKey.takeIf(Keys::isMouse) -> press(screen)
                    itemKey.takeIf(Keys::isMouse) -> pressItem(screen)
                    else -> return@register true
                }
                false
            }
            Refusals.allowMouseRelease(screen).register { _, event ->
                when {
                    pressing && Keys.isMouse(slotKey) && event.button() == slotKey -> release(client, screen)
                    itemKeyDown && Keys.isMouse(itemKey) && event.button() == itemKey -> itemKeyDown = false
                    else -> return@register true
                }
                false
            }
            ScreenEvents.afterForeground(screen).register { _, graphics, _, _, _ -> if (on) {
                    if (itemKeyDown) lockItem(screen, first = false)
                    if (locks.locked.isNotEmpty() || locks.items.isNotEmpty() || pressing) screen.drawInContainer(graphics) {
                        padlocks(screen, graphics)
                        link(screen, graphics)
                    }
                } }
            ScreenEvents.remove(screen).register {
                pressing = false
                itemKeyDown = false
                refused = null
                if (pending?.first === screen) pending = null
            }
        }
    }

    private fun press(screen: AbstractContainerScreen<*>) {
        pressing = true
        from = own(screen.slotUnderMouse)
    }

    private fun pressItem(screen: AbstractContainerScreen<*>) {
        itemKeyDown = true
        lockingItems = null
        lockItem(screen, first = true)
    }

    private fun lockItem(screen: AbstractContainerScreen<*>, first: Boolean) {
        val slot = screen.slotUnderMouse ?: return
        if (own(slot) == null || !slot.hasItem()) return
        val id = slot.item.itemUuid
        if (id == null) {
            if (first) Chat.notice(feature.feature.title, text { bad("An item that stacks cannot be locked. Lock its slot instead.") })
            return
        }
        val lock = lockingItems ?: (id !in locks.items).also { lockingItems = it }
        if ((id in locks.items) != lock) feature.locks = locks.item(id, lock)
    }

    private fun release(client: Minecraft, screen: AbstractContainerScreen<*>) {
        if (!pressing) return
        pressing = false
        val from = from ?: return
        val to = own(screen.slotUnderMouse) ?: return
        if (!on) return
        if (from == to) {
            feature.locks = locks.toggle(from)
            return
        }
        val linked = locks.link(from, to)
        if (linked == null) Chat.notice(feature.feature.title, text { bad("Link a hotbar slot with a slot in the rows above it.") }) else feature.locks = linked
    }

    @InputApproved(by = "noxaur", reaches = ["ContainerClicks.click"])
    fun refuses(screen: AbstractContainerScreen<*>, slot: Slot?, button: Int, input: ContainerInput): Boolean {
        if (!on) return false
        val client = Minecraft.getInstance()
        val own = own(slot)
        val partner = own?.let(locks::partner)
        if (input == ContainerInput.QUICK_MOVE && partner != null) {
            val above = find(screen, maxOf(own, partner))
            if (above != null) ContainerClicks.click(screen.menu, above.index, minOf(own, partner), ContainerInput.SWAP)
            return true
        }
        fun swapsWithLocked() = input == ContainerInput.SWAP && (button in locks.locked || client.player?.inventory?.getItem(button)?.itemUuid in locks.items)
        fun gathersFromLocked() = input == ContainerInput.PICKUP_ALL && screen.menu.slots.any { locked(it) && ItemStack.isSameItemSameComponents(screen.menu.carried, it.item) }
        val refuses = slot != null && locked(slot) || swapsWithLocked() || gathersFromLocked()
        if (refuses) {
            refused = slot ?: screen.menu.slots.firstOrNull { own(it) == button }
            refusedAt = Util.getMillis()
            return true
        }
        return input == ContainerInput.QUICK_MOVE && slot != null && slot.hasItem() && steered(client, screen, slot)
    }

    @InputApproved(by = "noxaur", reaches = ["ContainerClicks.click"])
    private fun steered(client: Minecraft, screen: AbstractContainerScreen<*>, slot: Slot): Boolean {
        val stack = slot.item
        val into = destinations(client, screen, slot) ?: return false
        val described = into.map { described(stack, it) }
        val yields = !slot.mayPlace(stack)
        val moving = stack.count
        val vanilla = places(stack, described, skipLocked = false)
        val opened = places(stack, described)
        val lockedTakes = vanilla.any { (index, _) -> described.first { it.index == index }.locked }
        val lockedCould = described.any { it.locked && (it.acceptsEmpty || it.same && it.count < it.limit) }
        if (opened.isEmpty()) {
            if (lockedTakes || yields && lockedCould) pending = null
            return lockedTakes || yields && lockedCould
        }
        if (!yields && !lockedTakes || yields && !lockedCould) return false
        // @keep A result cannot take a leftover back. Taking part of it consumes the craft and deletes the rest.
        if (yields && opened.sumOf { it.second } < moving) {
            pending = null
            return true
        }
        val carried = screen.menu.carried
        val reserved = described.map { it.index }.toSet() + slot.index
        val park = if (carried.isEmpty) null else park(screen, carried, reserved)
        if (!carried.isEmpty && park == null) {
            pending = null
            return true
        }
        if (client.gameMode == null || client.player == null) {
            pending = null
            return true
        }
        fun click(index: Int) = ContainerClicks.click(screen.menu, index, 0, ContainerInput.PICKUP)
        if (park != null) click(park.index)
        click(slot.index)
        // @keep A left click inserts min(cursor, room), which is the amount this slot was planned to take.
        for ((index, _) in places(screen.menu.carried, into.map { described(screen.menu.carried, it) })) {
            click(index)
        }
        if (!screen.menu.carried.isEmpty && slot.mayPlace(screen.menu.carried)) click(slot.index)
        if (park != null && screen.menu.carried.isEmpty) click(park.index)
        val gridLeft = yields && screen.menu.slots.any { it !== slot && own(it) == null && it.hasItem() }
        pending = if (gridLeft) screen to slot.index else null
        if (gridLeft) pendingWaits = 0
        return true
    }

    private fun park(screen: AbstractContainerScreen<*>, carried: ItemStack, reserved: Set<Int>): Slot? =
        screen.menu.slots.firstOrNull {
            val own = own(it)
            it.isActive && !it.hasItem() && it.index !in reserved && !locked(it) && it.mayPlace(carried) &&
                (own in SlotLocks.HOTBAR || own in SlotLocks.ABOVE)
        }

    private fun places(stack: ItemStack, slots: List<ShiftSlot>, skipLocked: Boolean = true) =
        shiftPlaces(stack.count, slots, skipLocked = skipLocked, stackable = stack.isStackable)

    private fun described(stack: ItemStack, slot: Slot): ShiftSlot {
        val item = slot.item
        val empty = !slot.hasItem()
        return ShiftSlot(
            slot.index,
            if (empty) 0 else item.count,
            slot.getMaxStackSize(if (empty) stack else item),
            !empty && ItemStack.isSameItemSameComponents(stack, item),
            empty && slot.mayPlace(stack),
            locked(slot),
        )
    }

    private fun destinations(client: Minecraft, screen: AbstractContainerScreen<*>, slot: Slot): List<Slot>? {
        val own = own(slot)
        val main = screen.menu.slots.filter { own(it) in 0..SlotLocks.ABOVE.last && it.isActive }
        return when {
            // @keep Out of a menu the game fills the inventory from its last slot back.
            own == null -> main.reversed()
            screen.menu !is InventoryMenu -> null
            else -> {
                // @keep Armor goes on when its slot is free, then the hotbar and the rows above trade with each other.
                val worn = client.player?.getEquipmentSlotForItem(slot.item)?.takeIf { it.isArmor }?.let { find(screen, it.getIndex(Inventory.INVENTORY_SIZE)) }
                listOfNotNull(worn?.takeIf { own in 0..SlotLocks.ABOVE.last }) + main.filter { own > SlotLocks.ABOVE.last || (own(it) in SlotLocks.HOTBAR) != (own in SlotLocks.HOTBAR) }
            }
        }
    }

    fun keepsHeld(): Boolean {
        val inventory = Minecraft.getInstance().player?.inventory ?: return false
        return on && (inventory.selectedSlot in locks.locked || inventory.selectedItem.itemUuid in locks.items)
    }

    private fun link(screen: AbstractContainerScreen<*>, graphics: GuiGraphicsExtractor) {
        val over = own(screen.slotUnderMouse) ?: return
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
            val role = if (own == null || own !in locks.locked) Role.GOOD else if (locks.partner(own) != null) Role.ACTION else Role.WARNING
            Kit.icon(graphics, Icon.LOCK, slot.x, slot.y, role.argb())
        }
    }
}
