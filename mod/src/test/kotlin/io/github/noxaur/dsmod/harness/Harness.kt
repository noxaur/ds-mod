package io.github.noxaur.dsmod.harness

import io.github.noxaur.dsmod.core.capture.Captured
import io.github.noxaur.dsmod.core.capture.readCaptureLine
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.feature.Saved
import io.github.noxaur.dsmod.core.feature.savedJson
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.SystemMessage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

abstract class Harness(
    val tree: SettingsTree = SettingsTree(),
    alone: KClass<out HasFeature>? = null,
    var time: Long = 1_000_000,
    onDragSim: () -> Boolean = { true },
) {
    val game = Game(if (alone == null) tree.features else listOf(tree.feature(alone.java).feature), connected = onDragSim, worker = Runnable::run)

    fun post(vararg events: GameEvent): Boolean {
        val shown = events.map { game.post(time, it) }.all { it }
        game.tick(time)
        return shown
    }

    fun say(time: Long, line: String) = game.post(time, chat(line))

    fun pass(millis: Long = Game.REFRESH_MILLIS) {
        time += millis
        game.tick(time)
    }

    fun leaveWhileOff(feature: HasFeature, vararg before: GameEvent) {
        post(*before)
        feature.feature.enabled = false
        post(LeftServer)
        feature.feature.enabled = true
        pass()
    }

    fun readout(feature: HasFeature? = null) =
        if (feature == null) game.readouts.single() else game.readouts.single { it.feature === feature.feature }

    fun hud(feature: HasFeature? = null) = readout(feature).hud

    fun rows(feature: HasFeature? = null) = hud(feature)?.rows?.map { it.text.plain }
}

fun text(plain: String, color: Int? = null) = ServerText(ServerSpan(plain, color))

fun text(vararg spans: ServerSpan) = ServerText(*spans)

fun lines(vararg plain: String) = plain.map { text(it) }

fun chat(line: String) = SystemMessage(text(line))

fun dragSimSidebar(vararg lines: ServerText) = Sidebar(text("DragSim"), listOf(text("Purse: 1,000")) + lines)

fun ownInventory(vararg slots: Pair<Int, Item?>): Array<GameEvent> =
    arrayOf<GameEvent>(ContainerOpened(0, null, 46)) + slots.map { (slot, item) -> SlotChanged(slot, item) }

fun resource(path: String) = checkNotNull(Harness::class.java.getResource("/$path")) { "No test resource /$path" }.readText()

fun readCapture(file: Path): List<Captured> = Files.readAllLines(file).filter { it.isNotBlank() }.mapNotNull(::readCaptureLine)

@Suppress("UNCHECKED_CAST")
fun Saved<*>.encode(): String = (this as Saved<Any>).let { savedJson.encodeToString(it.serializer, it.value) }
