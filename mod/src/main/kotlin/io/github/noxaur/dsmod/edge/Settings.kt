package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.RenderContext
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorAccordion
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Favorites
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import net.minecraft.client.Minecraft
import java.lang.reflect.Field

/**
 * The settings screen. [open] is the one way in, for the Settings Key, `/dsmod`, `/dsm` and every button that
 * returns to the settings, so the screen that replaces MoulConfig's takes over there alone.
 *
 * Today it is MoulConfig's editor with a star on each setting and a Favorites category on top, both fed by
 * [Favorites]. MoulConfig has no favorites, and it takes any categories and options it is handed, so each category
 * and option is wrapped. One editor lasts the session, so the screen opens where the player left it.
 */
object Settings {
    /**
     * MoulConfig fits a setting's name within 5 of the left third's edges and starts its description 5 past that
     * third, so the 10 between them are always empty. The star sits there, beside the name's first row.
     */
    private fun starX(x: Int, width: Int) = x + width / 3 - 4

    private const val STAR_TOP = 6

    private const val OFF_ALPHA = 110

    /** The star takes a press a pixel around it, as a 7x7 target is easy to miss. */
    private val STAR_TARGET = Icon.STAR.width + 2

    private lateinit var config: ManagedConfig<DsModConfig>

    /** Built on the first open, because MoulConfig's editors need the game's fonts. */
    private val editor: MoulConfigEditor<DsModConfig> by lazy(::build)

    /** The id of the category that lists the starred settings. */
    const val FAVORITES = "favorites"

    // What the screen shows, in plain types for the game tests, because the jar relocates MoulConfig.

    /** The paths of the settings Favorites lists now. */
    val favorites: List<String> get() = editor.allCategories.getValue(FAVORITES).options.map { it.path }

    /** The id of the category shown now. */
    val selected: String get() = editor.selectedCategory

    /** The ids of the categories listed now, which a search narrows. */
    val listed: Set<String> get() = editor.currentlyVisibleCategories.keys

    /** The id of the category that [setting] of `DsModConfig` is in. */
    fun category(setting: Field): String = editor.getOptionFromField(setting)!!.category.identifier

    /** Where the star of [setting] was last drawn, in GUI pixels. */
    fun star(setting: Field): Pair<Int, Int> = (editor.getOptionFromField(setting)!!.editor as Star).let { it.x to it.y }

    fun showFavorites() = editor.setSelectedCategory(editor.allCategories.getValue(FAVORITES))

    fun search(words: String) = editor.search(words)

    fun register(config: ManagedConfig<DsModConfig>) {
        this.config = config
    }

    /** Opens the settings where the player left them, at [setting] of `DsModConfig` when it is given, or searched for [search]. */
    fun open(setting: Field? = null, search: String? = null) {
        if (setting != null) editor.goToOption(editor.getOptionFromField(setting)!!)
        if (search != null) editor.search(search)
        // MoulConfig tells only a component that it closed, and the editor is wrapped in one that does not pass it on,
        // so without this the editor never saves.
        IMinecraft.INSTANCE.openWrappedScreen(
            object : GuiContext(GuiElementComponent(editor)) {
                override fun onAfterClose() {
                    super.onAfterClose()
                    editor.onAfterClose()
                }
            },
        )
    }

    /**
     * The game's own key event, which it sends only with no screen open, so a key typed into chat is not a press.
     * The settings open on the next tick, after the game has handed out the typed character, or the search would take it.
     */
    fun pressed(key: InputConstants.Key) {
        if (Keys.matches(key, config.instance.general.settingsKey)) Minecraft.getInstance().schedule { open() }
    }

    private fun build(): MoulConfigEditor<DsModConfig> {
        val categories = LinkedHashMap<String, ProcessedCategory>()
        val every = ArrayList<ProcessedOption>()
        categories[FAVORITES] = FavoritesCategory { Favorites.pick(every, config.instance.favorites) { it.path } }
        for ((id, real) in config.processor.allCategories) {
            val options = ArrayList<ProcessedOption>()
            val category = Wrapped(real, options)
            real.options.mapTo(options) { StarredOption(it, category) }
            every += options
            categories[id] = category
        }
        return MoulConfigEditor(categories, config.instance)
    }

    /** One of the player's categories, holding the starred version of each of its settings. */
    private class Wrapped(real: ProcessedCategory, private val options: List<ProcessedOption>) : ProcessedCategory by real {
        override fun getOptions() = options
    }

    /**
     * The Favorites category. Its settings are the ones in the other categories, which say they belong there, so a
     * search hides Favorites and finds each setting once, in its own category.
     */
    private class FavoritesCategory(private val pick: () -> List<ProcessedOption>) : ProcessedCategory {
        override fun getDisplayName(): StructuredText = StructuredText.of("Favorites")
        override fun getDescription(): StructuredText = StructuredText.of("Click a setting's star to pin it here.")
        override fun getIdentifier() = FAVORITES
        override fun getParentCategoryId(): String? = null
        override fun getOptions() = pick()
        override fun getAccordionAnchors() = emptyMap<Int, ProcessedOption>()
        override fun getDebugDeclarationLocation() = "ds-mod's favorites"
    }

    private class StarredOption(private val real: ProcessedOption, private val category: ProcessedCategory) :
        ProcessedOption by real,
        ProcessedOption.HasField {
        // An accordion is found by its editor's class, so it keeps its own and gets no star.
        private val editor = real.editor.let { if (it is GuiOptionEditorAccordion) it else Star(it) }

        override fun getEditor(): GuiOptionEditor = editor
        override fun getCategory() = category
        override fun getField(): Field = (real as ProcessedOption.HasField).field
    }

    /** A setting's own editor with a star in its top left corner, which a press turns on or off. */
    private class Star(private val inner: GuiOptionEditor) : GuiOptionEditor(inner.option) {
        /** Where the star was last drawn, for the game test that presses it. */
        var x = 0
        var y = 0

        private val on: Boolean get() = option.path in config.instance.favorites

        /** Whether the mouse is on the star of the box at [x], [y], [width] wide. */
        private fun over(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int) =
            mouseX - (starX(x, width) - 1) in 0 until STAR_TARGET && mouseY - (y + STAR_TOP - 1) in 0 until STAR_TARGET

        override fun render(context: RenderContext, x: Int, y: Int, width: Int) {
            inner.activeConfigGUI = activeConfigGUI
            inner.render(context, x, y, width)
            this.x = starX(x, width)
            this.y = y + STAR_TOP
            val mc = IMinecraft.INSTANCE
            val hover = over(x, y, width, mc.mouseX, mc.mouseY)
            // An unstarred star is faint, so a row of them does not crowd the names, and lights up under the mouse.
            val color = when {
                on -> Role.ACTION.argb()
                hover -> Role.BODY.argb()
                else -> Role.MUTED.argb(OFF_ALPHA)
            }
            Kit.icon(context, Icon.STAR, this.x, this.y, color)
            if (hover) context.scheduleDrawTooltip(mc.mouseX, mc.mouseY, listOf(StructuredText.of(if (on) "Click to unstar." else "Click to star.")))
        }

        override fun mouseInput(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int, event: MouseEvent): Boolean {
            if (event is MouseEvent.Click && event.mouseButton == 0 && over(x, y, width, mouseX, mouseY)) {
                if (event.mouseState) config.instance.favorites = Favorites.toggle(config.instance.favorites, option.path)
                return true
            }
            return inner.mouseInput(x, y, width, mouseX, mouseY, event)
        }

        override fun getHeight() = inner.height
        override fun getDescriptionHeight() = inner.descriptionHeight
        override fun keyboardInput(event: KeyboardEvent) = inner.keyboardInput(event)
        override fun mouseInputOverlay(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int, event: MouseEvent) =
            inner.mouseInputOverlay(x, y, width, mouseX, mouseY, event)
        override fun renderOverlay(context: RenderContext, x: Int, y: Int, width: Int) = inner.renderOverlay(context, x, y, width)
        override fun fulfillsSearch(word: String) = inner.fulfillsSearch(word)
        override fun setGuiContext(context: GuiContext) = inner.setGuiContext(context)
    }
}
