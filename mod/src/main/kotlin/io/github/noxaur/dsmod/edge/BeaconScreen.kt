package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.ProtectorRow
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.Sections
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitRangeSlider
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

private const val TITLE = "Boss Beacon"
private const val HINT_ADD = "Click the bar to add a section, and drag one to change it."
private const val HINT_REMOVE = "Right-click a section to remove it."
private const val SCALE = "Zealots killed since the last Protector"
private const val LABEL_HEIGHT = 8
private const val MAX = ProtectorRow.CYCLE_KILLS

// The server raises the Protector one tier for every 50 kills.
private val TIERS = listOf(50, 100, 150)
private val TIER_NAMES = listOf("Tier 1", "Tier 2", "Tier 3", "Tier 4")
private val DEFAULT = listOf(Section(175, 200))

/**
 * Picks the parts of the Protector's cycle that show Boss Beacon's beam: any number of sections of the 200
 * zealot kills between two Protectors, and the fight itself. Closing saves through [onSave].
 */
class BeaconScreen(private val settings: DsModConfig.End, private val onSave: () -> Unit) : Screen(text { body(TITLE) }.toComponent()) {
    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var left = 0
    private var top = 0
    private var scaleY = 0
    private var tiersY = 0
    private var readoutY = 0
    private var readout = ""
    private var tierX = IntArray(0)
    private lateinit var slider: KitRangeSlider
    private lateinit var reset: KitButton

    override fun init() {
        val content = maxOf(font.width(HINT_ADD), 300)
        panelWidth = content + Kit.INSET * 2
        panelHeight = Kit.INSET * 2 + Kit.BUTTON_HEIGHT * 3 + LABEL_HEIGHT * 5 + Kit.GAP * 8
        panelX = (width - panelWidth) / 2
        panelY = (height - panelHeight) / 2
        left = panelX + Kit.INSET
        top = panelY + Kit.INSET
        val doneWidth = font.width("Done") + 16
        addRenderableWidget(KitButton(panelX + panelWidth - Kit.INSET - doneWidth, top, "Done", ::onClose, doneWidth))

        scaleY = top + Kit.BUTTON_HEIGHT + Kit.GAP + (LABEL_HEIGHT + Kit.GAP) * 2
        val sliderY = scaleY + LABEL_HEIGHT + Kit.GAP
        slider = addRenderableWidget(KitRangeSlider(left, sliderY, content, SCALE, MAX, TIERS, settings.beamSections) {
            settings.beamSections = slider.sections
            refresh()
        })
        tiersY = sliderY + Kit.BUTTON_HEIGHT + 2
        // Each tier's name sits in the middle of its quarter of the track.
        tierX = IntArray(TIER_NAMES.size) { left + content * (2 * it + 1) / 8 - font.width(TIER_NAMES[it]) / 2 }
        readoutY = tiersY + LABEL_HEIGHT + Kit.GAP * 2

        val bottom = readoutY + LABEL_HEIGHT + Kit.GAP * 2
        val fight = addRenderableWidget(
            KitCheckbox(left, bottom + (Kit.BUTTON_HEIGHT - Kit.CHECKBOX_SIZE) / 2, "During the Fight", settings.beamInFight) { settings.beamInFight = it },
        )
        val resetWidth = font.width("Reset") + 16
        reset = addRenderableWidget(
            KitButton(panelX + panelWidth - Kit.INSET - resetWidth, bottom, "Reset", {
                settings.beamSections = DEFAULT
                settings.beamInFight = false
                fight.checked = false
                slider.sections = DEFAULT
                refresh()
            }, resetWidth),
        )
        refresh()
    }

    override fun removed() = onSave()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, panelX, panelY, panelWidth, panelHeight)
        Kit.label(graphics, TITLE, left, top + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        Kit.label(graphics, HINT_ADD, left, top + Kit.BUTTON_HEIGHT + Kit.GAP, Kit.Ink.MUTED)
        Kit.label(graphics, HINT_REMOVE, left, top + Kit.BUTTON_HEIGHT + Kit.GAP + LABEL_HEIGHT + Kit.GAP, Kit.Ink.MUTED)
        Kit.label(graphics, SCALE, left, scaleY)
        for (i in TIER_NAMES.indices) Kit.label(graphics, TIER_NAMES[i], tierX[i], tiersY, Kit.Ink.MUTED)
        Kit.label(graphics, readout, left, readoutY)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    /** What the section last touched covers, or what all of them cover together, in zealots and as a share of the cycle. */
    private fun refresh() {
        val sections = slider.sections
        val section = sections.getOrNull(slider.selected)
        readout = when {
            section != null ->
                "This section: ${section.from} to ${section.to} of $MAX zealots killed (${percent(section.from)} to ${percent(section.to)})"
            sections.isEmpty() -> "The beam never shows while the Protector rises."
            else -> Sections.covered(sections).let { "The beam shows for $it of $MAX zealot kills (${percent(it)})" }
        }
        reset.active = sections != DEFAULT || settings.beamInFight
    }

    private fun percent(kills: Int) = "${(kills * 100 + MAX / 2) / MAX}%"
}
