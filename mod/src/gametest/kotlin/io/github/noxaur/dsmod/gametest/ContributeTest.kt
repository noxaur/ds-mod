package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.backend.Requirement
import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import io.github.noxaur.dsmod.edge.contribute.ContributeScreen
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.minecraft.client.gui.components.AbstractWidget
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * Shows the Contribute popover in both themes, then runs a contribution with no backend to answer: a finished run
 * leaves a redacted, gzipped file and no raw one, and a run that outlasts its time leaves nothing.
 */
class ContributeTest : FabricClientGameTest {
    private val brief = Brief(
        "Golem tiers",
        listOf(
            Brief.Step("Warp to the End"),
            Brief.Step("Stand still next to the Protector until it spawns", "Do not hit it.", 90),
            Brief.Step("Kill it"),
        ),
    )

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use {
            val request = Request("r1", brief, listOf(Requirement("area", "The End")), 600)
            context.setScreen { ContributeScreen().also { it.offer(listOf(request)) } }
            context.waitTicks(2)
            context.takeScreenshot("contribute-charcoal")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
            context.waitTicks(2)
            context.takeScreenshot("contribute-vanilla")
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }
            context.setScreen { ContributeScreen().also { it.offer(emptyList()) } }
            context.waitTicks(2)
            context.takeScreenshot("contribute-none")
            // A window at Minecraft's smallest GUI height, 240, and a brief far longer than it.
            val long = Brief("Every tier", (1..30).map { Brief.Step("Do the thing for tier $it and wait for the next one", if (it % 3 == 0) "Do not hit it." else null) })
            for (theme in listOf(Theme.CHARCOAL, Theme.VANILLA)) {
                context.runOnClient<Nothing> { DsModClient.settings.hud.theme = theme }
                context.setScreen { ContributeScreen().also { it.offer(listOf(Request("r3", long, emptyList(), 600))) } }
                context.waitTicks(2)
                context.takeScreenshot("contribute-long-${theme.name.lowercase()}")
            }
            context.runOnClient<Nothing> { client ->
                DsModClient.settings.hud.theme = Theme.CHARCOAL
                val screen = client.gui.screen()!!
                check(screen.height <= 240) { "The window is ${screen.height} GUI pixels high, which is no small window" }
                val buttons = screen.children().filterIsInstance<AbstractWidget>()
                check(buttons.map { it.message.string } == listOf("Accept", "Skip", "Close")) { "The buttons are ${buttons.map { it.message.string }}" }
                buttons.forEach { check(it.y >= 0 && it.y + it.height <= screen.height) { "${it.message.string} is at ${it.y} on a screen ${screen.height} high" } }
            }
            context.setScreen { null }

            val dir = context.computeOnClient<Path, Nothing> { it.gameDirectory.toPath().resolve("dsmod/contributions") }
            finishedRun(context, dir)
            timedOutRun(context, dir)
        }
    }

    private fun press(context: ClientGameTestContext) {
        context.input.pressKey(GLFW.GLFW_KEY_RIGHT_BRACKET)
        context.waitTicks(2)
    }

    private fun files(dir: Path, glob: String) = if (dir.exists()) dir.listDirectoryEntries(glob) else emptyList()

    private fun finishedRun(context: ClientGameTestContext, dir: Path) {
        context.runOnClient<Nothing> { Recording.start(brief, "r1", 600) }
        context.waitTicks(2)
        context.takeScreenshot("contribute-run")
        repeat(4) { press(context) }
        // The second step is timed, so the key has moved on from it, and the last press ends the run.
        for (i in 0 until 200) {
            if (files(dir, "*.jsonl.gz").isNotEmpty()) break
            context.waitTicks(1)
        }
        val sent = files(dir, "*.jsonl.gz").single()
        check(files(dir, "*.jsonl").isEmpty()) { "The unredacted capture is still at ${files(dir, "*.jsonl")}" }
        check(!context.computeOnClient<Boolean, Nothing> { Recording.active || Recorder.running }) { "The run is still on" }
        val lines = GZIPInputStream(Files.newInputStream(sent)).bufferedReader().readLines()
        check(lines.count { "\"type\":\"step\"" in it } == 4) { "The upload's steps are ${lines.filter { "\"type\":\"step\"" in it }}" }
        Files.delete(sent)
    }

    private fun timedOutRun(context: ClientGameTestContext, dir: Path) {
        context.runOnClient<Nothing> { Recording.start(brief, "r2", 2) }
        press(context)
        check(context.computeOnClient<Boolean, Nothing> { Recorder.running }) { "The first press did not start the capture" }
        context.waitTicks(60)
        check(!context.computeOnClient<Boolean, Nothing> { Recording.active || Recorder.running }) { "The run outlasted its time" }
        for (i in 0 until 100) {
            if (files(dir, "*").isEmpty()) break
            context.waitTicks(1)
        }
        check(files(dir, "*").isEmpty()) { "A run that timed out left ${files(dir, "*")}" }
    }
}
