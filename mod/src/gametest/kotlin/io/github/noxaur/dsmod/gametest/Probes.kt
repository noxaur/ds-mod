package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.config.Items.Entry
import io.github.noxaur.dsmod.core.config.Slot
import io.github.noxaur.dsmod.core.config.Stored
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.ui.NavRow
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.edge.Alerts
import io.github.noxaur.dsmod.edge.Cameras
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.EditorButtons
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.HudLayoutEditor
import io.github.noxaur.dsmod.edge.PlayerPreview
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.edge.TabPlacesEditor
import io.github.noxaur.dsmod.edge.World
import io.github.noxaur.dsmod.edge.capture.Recording
import io.github.noxaur.dsmod.edge.profile.ProfileScreen
import io.github.noxaur.dsmod.edge.toComponent
import io.github.noxaur.dsmod.edge.ui.Editor
import io.github.noxaur.dsmod.edge.ui.ItemsEditor
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitNavTree
import io.github.noxaur.dsmod.edge.ui.SettingRows
import io.github.noxaur.dsmod.gametest.mixin.NarrationInvoker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.narration.ScreenNarrationCollector

class Spot(
    val x: Int, val y: Int, val width: Int, val height: Int,
    val controlX: Int, val controlY: Int, val controlWidth: Int, val controlHeight: Int,
    val extraX: Int, val extraWidth: Int, val markerX: Int, val markerY: Int,
    val starX: Int, val starY: Int,
    val sliderX: Int = -1, val sliderY: Int = -1, val sliderWidth: Int = 0,
)

fun SettingRows.spot(id: String, entry: Int = -1): Spot? {
    val l = lines.firstOrNull { it.setting.id == id && it.entry == entry } ?: return null
    val slider = l.slider
    return Spot(
        l.x, top(l), l.width, l.height, l.cx, origin + l.cy, l.cw, l.ch, l.ex, l.ew, l.nameX - 5, markerY(l),
        l.starX, if (l.starX < 0) -1 else origin + l.starY, slider?.x ?: -1, slider?.y ?: -1, slider?.width ?: 0,
    )
}

fun SettingsScreen.spot(id: String, entry: Int = -1): Spot? {
    sync()
    return rows.spot(id, entry)
}

fun SettingsScreen.line(section: String): Int? = nav.middle(NavRow(sections.indexOfFirst { it.name == section }))

fun SettingsScreen.shown() = rows.lines.filter { !it.heading && it.entry < 0 }.map { it.setting.id }

fun SettingsScreen.marked(id: String) = rows.lines.firstOrNull { it.setting.id == id && it.entry < 0 }?.changed == true

fun SettingsScreen.editor(id: String): Editor? {
    sync()
    return rows.made[id]
}

fun SettingsScreen.reveal(id: String) = rows.reveal(id)

fun SettingsScreen.narration(): String =
    ScreenNarrationCollector().apply { update((this@narration as NarrationInvoker)::invokeUpdateNarrationState) }.collectNarrationText(true)

fun KitNavTree.middle(row: NavRow): Int? {
    val i = tree.rows.indexOf(row).takeIf { it >= 0 } ?: return null
    return y + (tops[i] + tops[i + 1]) / 2 - scroll.offset
}

fun EditorButtons.middle(i: Int) = x(i) + width(i) / 2

fun ItemsEditor<*>.spot(line: Int, control: String? = null): IntArray? {
    val at = lines.getOrNull(line) ?: return null
    if (control == null) return intArrayOf(x, origin + at.y, width, at.height)
    val hit = at.hits.firstOrNull { it.caption == control } ?: return null
    return intArrayOf(hit.x, origin + hit.y, hit.width, hit.height)
}

val ItemsEditor<*>.size get() = lines.size

fun ItemsEditor<*>.lineOf(index: Int, details: Boolean = false) = indexOf(Entry.Item(index), details)

fun ItemsEditor<*>.headingOf(group: String, details: Boolean = false) = indexOf(Entry.Heading(group), details)

private fun ItemsEditor<*>.indexOf(entry: Entry, details: Boolean) =
    lines.indexOfFirst { it.entry == entry && (it.kind == ItemsEditor.Kind.DETAILS) == details }

fun TabPlacesEditor.fieldSpot(which: Int): IntArray = fields[which].let { intArrayOf(it.x, it.y, it.width, it.height) }

fun TabPlacesEditor.placeAt(edge: TabEdge, slot: Int): Pair<Int, Int> =
    box.x + TabLayout.x(slot) + Kit.TAB_WIDTH / 2 to tabY(edge) + Kit.TAB_HEIGHT / 2

fun HudLayoutEditor.pointOf(id: String): Pair<Int, Int>? {
    val block = Hud.find(id) ?: return null
    return (mapX + (block.x + block.width * block.scale / 2) * map.scale).toInt() to
        (box.origin + box.top + (block.y + block.height * block.scale / 2) * map.scale).toInt()
}

val PlayerPreview.yaw get() = turntable.yaw(0L)

val PlayerPreview.pitch get() = turntable.pitch

val ProfileScreen.overflows get() = maxScroll() > 0

val Cameras.looking get() = look != null

fun DsModClient.restart(between: () -> Unit = {}) {
    saved.close()
    between()
    start()
}

fun World.marksQueued() = shown.size + beamsShown.size + tracersShown.size

fun Recording.start(brief: Brief) {
    startAtLaunch(brief)
    Minecraft.getInstance().updateTitle()
}

fun Alerts.actionBar(text: Text) = Minecraft.getInstance().gui.hud.setOverlayMessage(text.toComponent(), false)

fun Stored.reset() = all.forEach { reset(it) }

private fun <T> reset(slot: Slot<T>) {
    slot.value = slot.default
}
