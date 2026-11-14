package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.debug.DsModDebug
import io.github.noxaur.dsmod.debug.brief.Brief
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
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
            context.runOnClient<Nothing> { DsModDebug.showBrief(it, brief) }
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
            val steps = captures.listDirectoryEntries("*.jsonl").maxBy { it.fileName.toString() }.readLines().filter { "\"type\":\"step\"" in it }
            check(steps.map { it.substringAfter("\"data\":") } == listOf(
                """{"index":1,"do":"Warp to the End"}}""",
                """{"index":2,"do":"Stand still next to the Protector until it spawns"}}""",
                """{"index":3,"do":"Kill it"}}""",
                """{"done":true}}""",
            )) { "The capture's steps are $steps" }

            context.runOnClient<Nothing> { DsModDebug.showBrief(it, null) }
        }
    }
}
