package io.github.noxaur.dsmod.gametest

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.debug.DsModDebug
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines

/** Runs a brief with the developer's key, and checks the window's title, the checklist and the `step` records in the capture. */
class BriefTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use {
            val brief = Brief(
                "Golem tiers",
                listOf(
                    Brief.Step("Warp to the End"),
                    Brief.Step("Stand still next to the Protector until it spawns", "Do not hit it. The head climbs a block per tier.", 1),
                    Brief.Step("Kill it"),
                ),
            )
            context.runOnClient<Nothing> { Recording.start(brief) }
            context.waitTicks(2)
            val title = context.computeOnClient<String?, Nothing> { GLFW.glfwGetWindowTitle(it.window.handle()) }
            check(title.orEmpty().startsWith("Golem tiers | ")) { "The window is titled '$title'" }
            context.takeScreenshot("brief-waiting")

            context.input.pressKey(GLFW.GLFW_KEY_RIGHT_BRACKET)
            context.input.pressKey(GLFW.GLFW_KEY_RIGHT_BRACKET)
            context.waitTicks(2)
            context.takeScreenshot("brief-timed-step")
            // The second step's one second runs out by itself.
            context.waitTicks(30)
            context.input.pressKey(GLFW.GLFW_KEY_RIGHT_BRACKET)
            context.waitTicks(2)
            context.takeScreenshot("brief-saved")

            val captures = context.computeOnClient<java.nio.file.Path, Nothing> { it.gameDirectory.toPath().resolve("dsmod/captures") }
            fun newestSteps() = captures.listDirectoryEntries("*.jsonl").maxBy { it.fileName.toString() }.readLines().filter { "\"type\":\"step\"" in it }
            // The file is finished on another thread, so wait for its last step instead of a number of ticks.
            context.waitFor { newestSteps().size == 4 }
            val steps = newestSteps()
            check(steps.map { it.substringAfter("\"data\":") } == listOf(
                """{"index":1,"do":"Warp to the End"}}""",
                """{"index":2,"do":"Stand still next to the Protector until it spawns"}}""",
                """{"index":3,"do":"Kill it"}}""",
                """{"done":true}}""",
            )) { "The capture's steps are $steps" }

            context.runOnClient<Nothing> { Recording.cancel() }

            // Both presses land before one tick reads the key, and each step still gets its own record.
            context.runOnClient<Nothing> { Recording.start(brief) }
            context.waitTicks(2)
            context.runOnClient<Nothing> {
                val key = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_BRACKET)
                repeat(2) { KeyMapping.click(key) }
            }
            context.waitTicks(2)
            // `/dscapture` must leave a brief's recording alone.
            var refused: String? = null
            val stolen = context.computeOnClient<java.nio.file.Path?, Nothing> { client -> DsModDebug.toggleCapture(client) { refused = it } }
            check(stolen == null && refused.orEmpty().startsWith("A brief is on")) { "/dscapture answered '$refused' with $stolen during a brief" }
            check(context.computeOnClient<Boolean, Nothing> { Recorder.running }) { "/dscapture stopped the brief's recording" }
            context.runOnClient<Nothing> {
                repeat(2) { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_BRACKET)) }
            }
            context.waitFor { newestSteps().size == 4 }
            val both = newestSteps()
            check(both.map { it.substringAfter("\"data\":") } == listOf(
                """{"index":1,"do":"Warp to the End"}}""",
                """{"index":2,"do":"Stand still next to the Protector until it spawns"}}""",
                """{"index":3,"do":"Kill it"}}""",
                """{"done":true}}""",
            )) { "Two presses in one tick recorded $both" }
            context.runOnClient<Nothing> { Recording.cancel() }

            // A brief run inside `/dscapture`'s capture records its steps there, and leaves it running after the last.
            val own = context.computeOnClient<java.nio.file.Path, Nothing> { DsModDebug.toggleCapture(it)!! }
            context.runOnClient<Nothing> { Recording.start(brief) }
            context.waitTicks(2)
            context.runOnClient<Nothing> { repeat(4) { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_BRACKET)) } }
            context.waitTicks(2)
            check(context.computeOnClient<Boolean, Nothing> { Recorder.running }) { "The brief ended /dscapture's capture" }
            context.runOnClient<Nothing> { Recording.cancel() }
            check(context.computeOnClient<Boolean, Nothing> { Recorder.running }) { "Cancelling the brief ended /dscapture's capture" }
            var saved: String? = null
            context.runOnClient<Nothing> { client -> DsModDebug.toggleCapture(client) { saved = it } }
            context.waitFor { saved != null }
            check(own.readLines().count { "\"type\":\"step\"" in it } == 4) { "/dscapture's capture holds ${own.readLines().filter { "step" in it }}" }

            // Cancelling a brief deletes the capture it started.
            val before = captures.listDirectoryEntries("*.jsonl").toSet()
            context.runOnClient<Nothing> { Recording.start(brief) }
            context.runOnClient<Nothing> { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_BRACKET)) }
            // The writer creates the file on its own thread.
            context.waitFor { (captures.listDirectoryEntries("*.jsonl") - before).isNotEmpty() }
            val started = (captures.listDirectoryEntries("*.jsonl") - before).single()
            context.runOnClient<Nothing> { Recording.cancel() }
            context.waitFor { !started.exists() }
        }
    }
}
