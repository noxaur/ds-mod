package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.container.MenuButton
import io.github.noxaur.dsmod.core.feature.inventory.FixedMenuLayout
import io.github.noxaur.dsmod.core.feature.inventory.KeepCursor
import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.ui.CursorMemory
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Menus
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.client.gui.screens.inventory.BeaconScreen
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.inventory.LoomScreen
import net.minecraft.client.gui.screens.inventory.MerchantScreen
import net.minecraft.client.gui.screens.inventory.SmithingScreen
import net.minecraft.client.gui.screens.inventory.StonecutterScreen
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.FurnaceResultSlot
import net.minecraft.world.inventory.MerchantResultSlot
import net.minecraft.world.inventory.NonInteractiveResultSlot
import net.minecraft.world.inventory.ResultSlot
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object MenuSkin {
    val cursor = CursorMemory()

    private lateinit var game: Game
    private lateinit var theme: MenuTheme
    private lateinit var fixedLayout: FixedMenuLayout
    private lateinit var keepCursor: KeepCursor

    fun bind(game: Game, theme: MenuTheme, fixedLayout: FixedMenuLayout, keepCursor: KeepCursor) {
        this.game = game
        this.theme = theme
        this.fixedLayout = fixedLayout
        this.keepCursor = keepCursor
    }

    val themed: Boolean get() = theme.feature.runs

    val stable: Boolean get() = fixedLayout.feature.runs

    val keepsCursor: Boolean get() = keepCursor.feature.runs

    private var buttonMenu: AbstractContainerMenu? = null
    private var buttonStacks = arrayOfNulls<ItemStack>(0)
    private var buttons = arrayOfNulls<MenuButton>(0)

    private var painting: AbstractContainerScreen<*>? = null
    private var paintGlass = -1
    private var mouseX = 0
    private var mouseY = 0
    private var face = Kit.State.IDLE

    private enum class Look(val suffix: String, val state: Kit.State, val selected: Boolean = false) {
        PLAIN("", Kit.State.IDLE),
        HIGHLIGHTED("_highlighted", Kit.State.HOVER),
        DISABLED("_disabled", Kit.State.OFF),
        SELECTED("_selected", Kit.State.IDLE, true),
    }

    private class Box {
        var x = 0
        var y = 0
        var width = 0
        var height = 0
        var partX = 0
        var partY = 0
        var partWidth = 0
        var partHeight = 0
        val wide get() = partWidth / width.toFloat()
        val tall get() = partHeight / height.toFloat()
    }

    private fun interface Part {
        fun draw(g: GuiGraphicsExtractor, b: Box)
    }

    private val box = Box()

    private val parts = HashMap<Identifier, Part>().apply {
        fun part(path: String, part: Part) = put(Identifier.withDefaultNamespace(path), part)
        for (furnace in listOf("furnace", "blast_furnace", "smoker")) {
            part("container/$furnace/lit_progress") { g, b -> flame(g, b) }
            part("container/$furnace/burn_progress") { g, b -> arrow(g, b, b.wide, PROGRESS) }
        }
        part("container/brewing_stand/fuel_length") { g, b -> fill(g, b, FIRE) }
        part("container/brewing_stand/brew_progress") { g, b -> arrow(g, b, b.tall, PROGRESS, true) }
        part("container/brewing_stand/bubbles") { g, b -> bubbles(g, b) }
        for (error in listOf("anvil/error", "grindstone/error", "smithing/error", "cartography_table/error", "loom/error", "villager/out_of_stock")) {
            part("container/$error") { g, b -> errorMark(g, b.x + b.width / 2, b.y + b.height / 2) }
        }
        val wells = listOf("anvil/text_field", "anvil/text_field_disabled", "cartography_table/map", "cartography_table/scaled_map", "cartography_table/duplicated_map")
        for (sprite in wells) part("container/$sprite") { g, b -> well(g, b.x, b.y, b.width, b.height) }
        val faces = mapOf(
            "container/enchanting_table/enchantment_slot" to listOf(Look.PLAIN, Look.HIGHLIGHTED, Look.DISABLED),
            "container/stonecutter/recipe" to listOf(Look.PLAIN, Look.HIGHLIGHTED, Look.SELECTED),
            "container/loom/pattern" to listOf(Look.PLAIN, Look.HIGHLIGHTED, Look.SELECTED),
            "container/beacon/button" to Look.entries,
            "widget/button" to listOf(Look.PLAIN, Look.HIGHLIGHTED, Look.DISABLED),
        )
        for ((sprite, looks) in faces) for (look in looks) part(sprite + look.suffix) { g, b -> face(g, b, look.state, look.selected) }
        for (level in 1..3) {
            val label = level.toString()
            part("container/enchanting_table/level_$level") { g, b -> centered(g, label, b, Kit.Ink.NORMAL) }
            part("container/enchanting_table/level_${level}_disabled") { g, b -> centered(g, label, b, Kit.Ink.OFF) }
        }
        for (scroller in listOf("stonecutter", "loom", "villager", "creative_inventory")) {
            part("container/$scroller/scroller") { g, b -> thumb(g, b, hover(b)) }
            part("container/$scroller/scroller_disabled") { g, b -> thumb(g, b, Kit.State.OFF) }
        }
        part("container/villager/experience_bar_background") { g, b -> well(g, b.x - 1, b.y - 1, b.width + 2, b.height + 2) }
        part("container/villager/experience_bar_current") { g, b -> fill(g, b, XP) }
        part("container/villager/experience_bar_result") { g, b -> fill(g, b, XP_GAINED) }
        part("container/villager/trade_arrow") { g, b -> icon(g, Icon.ARROW, b, ink(face)) }
        part("container/villager/trade_arrow_out_of_stock") { g, b -> icon(g, Icon.CROSS, b, BAD) }
        part("container/beacon/confirm") { g, b -> icon(g, Icon.CHECK, b, ink(face)) }
        part("container/beacon/cancel") { g, b -> icon(g, Icon.CROSS, b, ink(face)) }
        part("container/cartography_table/locked") { g, b -> icon(g, Icon.LOCK, b, Kit.GLASS_INK) }
        part("container/crafter/disabled_slot") { g, b ->
            Kit.slotDim(g, b.x + 1, b.y + 1)
            icon(g, Icon.CROSS, b, MUTED)
        }
        for (powered in listOf(true, false)) {
            part("container/crafter/${if (powered) "powered" else "unpowered"}_redstone") { g, b -> arrow(g, b, if (powered) 1f else 0f, POWER) }
        }
        for (sprite in listOf("container/slot", "container/horse/chest_slots")) part(sprite) { _, _ -> }
        for (column in 1..7) for (edge in TabEdge.entries) for (selected in listOf(true, false)) {
            val row = if (edge == TabEdge.TOP) "top" else "bottom"
            part("container/creative_inventory/tab_${row}_${if (selected) "selected" else "unselected"}_$column") { g, b -> tab(g, b, edge, selected) }
        }
    }

    fun register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR) { element ->
            HudElement { graphics, delta -> if (!glassOpen()) element.extractRenderState(graphics, delta) }
        }
        ScreenHooks.onEveryLayout<ContainerScreen> { screen, _ -> ScreenEvents.afterTick(screen).register { findButtons(screen.menu) } }
    }

    private fun findButtons(menu: ChestMenu) {
        val size = menu.rowCount * 9
        if (menu !== buttonMenu) {
            buttonMenu = menu
            buttonStacks = arrayOfNulls(size)
            buttons = arrayOfNulls(size)
        }
        for (i in 0 until size) {
            val stack = menu.slots[i].item
            if (stack === buttonStacks[i]) continue
            buttonStacks[i] = stack
            buttons[i] = if (stack.isEmpty) null else MenuButton.of(stack.id, stack.plainName)
        }
    }

    fun button(screen: AbstractContainerScreen<*>, index: Int): MenuButton? =
        if (screen.menu === buttonMenu && skins(screen)) buttons.getOrNull(index) else null

    fun drawButton(graphics: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>, slot: Slot, button: MenuButton) {
        val hovered = screen.slotUnderMouse === slot
        val icon = when (button) {
            MenuButton.BACK -> Icon.BACK
            MenuButton.CLOSE -> Icon.CROSS
            MenuButton.NEXT -> Icon.RIGHT
            MenuButton.PREVIOUS -> Icon.LEFT
        }
        val state = Kit.state(true, hovered, hovered && Minecraft.getInstance().mouseHandler.isLeftPressed)
        Kit.button(graphics, slot.x, slot.y, Kit.BUTTON_HEIGHT, Kit.BUTTON_HEIGHT, state, icon, glass(screen) >= 0)
    }

    fun skins(screen: AbstractContainerScreen<*>) = themed && screen.javaClass.packageName == VANILLA_SCREENS

    val glassAlpha: Int
        get() {
            if (!themed) return -1
            return if (theme.menuLook.value == MenuLook.GLASS) Menus.glassAlpha(theme.glassOpacity.value) else -1
        }

    fun glass(screen: AbstractContainerScreen<*>): Int = if (skins(screen)) glassAlpha else -1

    private fun glassOpen(): Boolean {
        val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*> ?: return false
        return glass(screen) >= 0
    }

    fun paint(screen: Screen, mouseX: Int, mouseY: Int) {
        val menu = screen as? AbstractContainerScreen<*>
        painting = if (menu != null && skins(menu)) menu else null
        paintGlass = if (painting != null) glassAlpha else -1
        this.mouseX = mouseX
        this.mouseY = mouseY
    }

    fun painted() {
        painting = null
    }

    fun texture(g: GuiGraphicsExtractor, texture: Identifier, u: Float, v: Float): Boolean {
        val screen = painting ?: return false
        if (texture.namespace != Identifier.DEFAULT_NAMESPACE || !texture.path.startsWith(CONTAINER_TEXTURES)) return false
        if (u == 0f && v == 0f) background(g, screen, texture)
        return true
    }

    fun sprite(g: GuiGraphicsExtractor, sprite: Identifier, x: Int, y: Int, width: Int, height: Int, partX: Int, partY: Int, partWidth: Int, partHeight: Int): Identifier {
        val screen = painting ?: return sprite
        val part = parts[sprite] ?: return sprite
        if (sprite.path.startsWith(WIDGETS) && screen !is MerchantScreen) return sprite
        val b = box
        b.x = x
        b.y = y
        b.width = width
        b.height = height
        b.partX = partX
        b.partY = partY
        b.partWidth = partWidth
        b.partHeight = partHeight
        part.draw(g, b)
        return DRAWN
    }

    private fun background(g: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>, texture: Identifier) = game.metrics.time("menus.draw", Metrics.FRAME) {
        val glass = paintGlass
        val solid = glass < 0
        val left = screen.containerLeft
        val top = screen.containerTop
        if (solid) Kit.panel(g, left, top, screen.containerWidth, screen.containerHeight) else Kit.glass(g, left, top, screen.containerWidth, screen.containerHeight, glass)
        when (screen) {
            is InventoryScreen -> well(g, left + 25, top + 7, 51, 72)
            is AbstractFurnaceScreen<*> -> {
                Kit.flame(g, left + 56, top + 36, 0f, FIRE, !solid)
                Kit.arrow(g, left + 79, top + 34, 24, 16, 0f, PROGRESS, glass = !solid)
            }
            is BrewingStandScreen -> {
                well(g, left + 59, top + 43, 20, 6)
                Kit.arrow(g, left + 97, top + 16, 9, 28, 0f, PROGRESS, true, !solid)
                Kit.bubbles(g, left + 63, top + 14, 0f, PROGRESS, !solid)
            }
            is AnvilScreen -> {
                centered(g, PLUS, left + 43, top + 47, 33, 16, Kit.Ink.NORMAL)
                arrowAt(g, left + 113, top + 55)
            }
            is GrindstoneScreen -> arrowAt(g, left + 106, top + 41)
            is SmithingScreen -> arrowAt(g, left + 79, top + 56)
            is CartographyTableScreen -> {
                centered(g, PLUS, left + 15, top + 31, 16, 21, Kit.Ink.NORMAL)
                arrowAt(g, left + 49, top + 41)
            }
            is StonecutterScreen -> {
                well(g, left + 51, top + 14, 66, 56)
                well(g, left + 118, top + 14, 14, 58)
            }
            is LoomScreen -> {
                well(g, left + 59, top + 12, 58, 58)
                well(g, left + 118, top + 12, 14, 58)
                well(g, left + 139, top + 7, 22, 42)
            }
            is BeaconScreen -> {
                well(g, left + 8, top + 6, 107, 96)
                well(g, left + 116, top + 6, 106, 96)
                for (tier in 0..2) pyramid(g, left + 30, top + 33 + tier * 25 - (tier + 1) * 2, tier + 1)
                pyramid(g, left + 167, top + 26, 4)
            }
            is MerchantScreen -> {
                well(g, left + 4, top + 17, 89, 142)
                well(g, left + 93, top + 17, 8, 142)
                arrowAt(g, left + 196, top + 45)
            }
            is AbstractMountInventoryScreen<*> -> well(g, left + 25, top + 17, 54, 54)
            is CreativeModeInventoryScreen -> when {
                texture.path.endsWith(CREATIVE_INVENTORY) -> well(g, left + 72, top + 5, 34, 45)
                else -> {
                    well(g, left + 174, top + 17, 14, 112)
                    if (texture.path.endsWith(CREATIVE_SEARCH)) well(g, left + 80, top + 4, 89, 12)
                }
            }
        }
        val slots = screen.menu.slots
        var result: ResultSlot? = null
        for (slot in slots) {
            if (!slot.isActive) continue
            if (slot is ResultSlot) result = slot
            if (big(screen, slot)) {
                val x = left + slot.x - 5
                val y = top + slot.y - 5
                Kit.slotFrame(g, x, y, !solid, Kit.RESULT_SLOT_SIZE)
            } else if (!Slots.hides(slot.index) && button(screen, slot.index) == null) {
                Kit.slotFrame(g, left + slot.x - 1, top + slot.y - 1, !solid)
            }
        }
        result ?: return@time
        var gridRight = 0
        for (slot in slots) if (slot.x < result.x && slot.y in result.y - 17..result.y + 17) gridRight = maxOf(gridRight, slot.x + 17)
        val icon = Icon.ARROW
        val x = left + (gridRight + result.x - 5 - icon.width) / 2
        Kit.icon(g, icon, x, top + result.y + (16 - icon.height) / 2, ink(glass), inkShadow(glass))
    }

    private fun big(screen: AbstractContainerScreen<*>, slot: Slot) =
        slot is ResultSlot || slot is FurnaceResultSlot || slot is MerchantResultSlot || slot is NonInteractiveResultSlot ||
            screen is StonecutterScreen && slot.index == 1 || screen is LoomScreen && slot.index == 3 || screen is CartographyTableScreen && slot.index == 2

    private fun glassy() = paintGlass >= 0

    private fun well(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) =
        if (glassy()) Kit.glassWell(g, x, y, width, height) else Kit.well(g, x, y, width, height)

    private fun face(g: GuiGraphicsExtractor, b: Box, base: Kit.State, selected: Boolean) {
        val state = if (base == Kit.State.HOVER && Minecraft.getInstance().mouseHandler.isLeftPressed) Kit.State.DOWN else base
        Kit.buttonFace(g, b.x, b.y, b.width, b.height, state, glassy())
        if (selected) Kit.ring(g, b.x, b.y, b.width, b.height, Kit.theme.accent)
        face = state
    }

    private fun flame(g: GuiGraphicsExtractor, b: Box) = Kit.flame(g, b.x, b.y, b.tall, FIRE, glassy())

    private fun bubbles(g: GuiGraphicsExtractor, b: Box) = Kit.bubbles(g, b.x, b.y, b.tall, PROGRESS, glassy())

    private fun arrow(g: GuiGraphicsExtractor, b: Box, fraction: Float, color: Int, down: Boolean = false) =
        Kit.arrow(g, b.x, b.y, b.width, b.height, fraction, color, down, glassy())

    private fun fill(g: GuiGraphicsExtractor, b: Box, color: Int) = Kit.barFill(g, b.partX, b.partY, b.partWidth, b.partHeight, color)

    private fun thumb(g: GuiGraphicsExtractor, b: Box, state: Kit.State) = Kit.thumb(g, b.x, b.y, b.width, b.height, state, glassy())

    private fun hover(b: Box) =
        if (mouseX >= b.x && mouseY >= b.y && mouseX < b.x + b.width && mouseY < b.y + b.height) Kit.State.HOVER else Kit.State.IDLE

    private fun tab(g: GuiGraphicsExtractor, b: Box, edge: TabEdge, selected: Boolean) {
        val panelY = if (edge == TabEdge.TOP) b.y + b.height - CREATIVE_TAB_OVERLAP else b.y + CREATIVE_TAB_OVERLAP
        val hovered = !selected && hover(b) == Kit.State.HOVER
        if (glassy()) {
            Kit.glassTab(g, b.x, panelY, selected, paintGlass, edge, hovered, b.width)
        } else {
            Kit.tab(g, b.x, panelY, selected, edge, hovered, b.width, CREATIVE_TAB_HEIGHT)
        }
    }

    private fun ink(state: Kit.State) = if (state == Kit.State.OFF) {
        if (glassy()) Kit.GLASS_INK_OFF else Kit.theme.inkOff
    } else {
        ink(paintGlass)
    }

    private fun icon(g: GuiGraphicsExtractor, icon: Icon, b: Box, color: Int) =
        Kit.icon(g, icon, b.x + (b.width - icon.width) / 2, b.y + (b.height - icon.height) / 2, color, glassy() || Kit.theme.inkShadow)

    private fun centered(g: GuiGraphicsExtractor, label: String, b: Box, ink: Kit.Ink) = centered(g, label, b.x, b.y, b.width, b.height, ink)

    private fun centered(g: GuiGraphicsExtractor, label: String, x: Int, y: Int, width: Int, height: Int, ink: Kit.Ink) =
        Kit.label(g, label, x + (width - Kit.font.width(label) + 1) / 2, y + (height - Kit.LABEL_HEIGHT) / 2, ink, glassy())

    private fun arrowAt(g: GuiGraphicsExtractor, centerX: Int, centerY: Int) {
        val icon = Icon.ARROW
        Kit.icon(g, icon, centerX - icon.width / 2, centerY - icon.height / 2, ink(paintGlass), inkShadow(paintGlass))
    }

    private fun errorMark(g: GuiGraphicsExtractor, centerX: Int, centerY: Int) {
        well(g, centerX - ERROR_SIZE / 2, centerY - ERROR_SIZE / 2, ERROR_SIZE, ERROR_SIZE)
        val icon = Icon.CROSS
        Kit.icon(g, icon, centerX - icon.width / 2, centerY - icon.height / 2, BAD)
    }

    private fun pyramid(g: GuiGraphicsExtractor, centerX: Int, top: Int, layers: Int) {
        for (layer in 0 until layers) {
            val width = PYRAMID_TOP + layer * PYRAMID_STEP
            Kit.buttonFace(g, centerX - width / 2, top + layer * PYRAMID_LAYER, width, PYRAMID_LAYER, Kit.State.IDLE, glassy())
        }
    }

    fun ink(glass: Int) = if (glass < 0) Kit.theme.ink else Kit.GLASS_INK

    fun inkShadow(glass: Int) = glass >= 0 || Kit.theme.inkShadow

    fun offerInk(screen: AbstractContainerScreen<*>, color: Int): Int {
        if (!skins(screen)) return color
        val glass = glass(screen)
        return when (color) {
            OFFER, OFFER_HOVERED, OFFER_COST -> ink(glass)
            else -> if (glass >= 0) Kit.GLASS_INK_OFF else Kit.theme.inkOff
        }
    }

    fun offerShadow(screen: AbstractContainerScreen<*>, shadow: Boolean) = if (skins(screen)) inkShadow(glass(screen)) else shadow

    fun recipeButton(graphics: GuiGraphicsExtractor, button: AbstractWidget): Boolean {
        val client = Minecraft.getInstance()
        val screen = client.gui.screen() as? AbstractContainerScreen<*> ?: return false
        if (!skins(screen)) return false
        val pressed = button.isHovered && client.mouseHandler.isLeftPressed
        Kit.button(graphics, button.x, button.y, button.width, button.height, Kit.state(button.active, button.isHoveredOrFocused, pressed), Icon.BOOK, glass(screen) >= 0)
        return true
    }

    private val DRAWN: Identifier = Identifier.fromNamespaceAndPath("dsmod", "drawn")
    private const val VANILLA_SCREENS = "net.minecraft.client.gui.screens.inventory"
    private const val CONTAINER_TEXTURES = "textures/gui/container/"
    private const val CREATIVE_INVENTORY = "tab_inventory.png"
    private const val CREATIVE_SEARCH = "tab_item_search.png"
    private const val CREATIVE_TAB_OVERLAP = 4
    private const val CREATIVE_TAB_HEIGHT = 24
    private const val PLUS = "+"
    private const val WIDGETS = "widget/"
    private const val ERROR_SIZE = 11
    private const val PYRAMID_TOP = 4
    private const val PYRAMID_STEP = 6
    private const val PYRAMID_LAYER = 4
    private const val OFFER = -9937334
    private const val OFFER_HOVERED = -128
    private const val OFFER_COST = -8323296
    private val FIRE = Role.WARNING.argb()
    private val PROGRESS = Role.BODY.argb()
    private val POWER = Role.BAD.argb()
    private val BAD = Role.BAD.argb()
    private val MUTED = Role.MUTED.argb()
    private val XP = Role.GOOD.argb()
    private val XP_GAINED = Role.VALUE.argb()
}
