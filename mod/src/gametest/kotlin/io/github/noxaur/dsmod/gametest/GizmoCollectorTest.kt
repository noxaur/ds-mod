package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.BoxStyle
import io.github.noxaur.dsmod.edge.World
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.minecraft.world.phys.AABB
import java.nio.file.Path
import javax.imageio.ImageIO

class GizmoCollectorTest : FabricClientGameTest {
    @Volatile
    private var drawing = false

    private val box = World.FrameBox(AABB(6.0, 100.6, -0.5, 8.0, 102.6, 1.5), Role.GOOD, BoxStyle.OUTLINE, 4f, true, null)

    private fun near(screenshot: Path, rgb: Int): Int {
        val image = ImageIO.read(screenshot.toFile())
        var count = 0
        for (y in image.height / 4 until image.height * 3 / 4) for (x in image.width / 4 until image.width * 3 / 4) {
            val pixel = image.getRGB(x, y)
            val distance = Math.abs((pixel shr 16 and 0xFF) - (rgb shr 16 and 0xFF)) +
                Math.abs((pixel shr 8 and 0xFF) - (rgb shr 8 and 0xFF)) + Math.abs((pixel and 0xFF) - (rgb and 0xFF))
            if (distance < 60) count++
        }
        return count
    }

    override fun runTest(context: ClientGameTestContext) {
        LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register { _, _ -> if (drawing) World.draw(box) }
        context.worldBuilder().create().use { world ->
            world.server.runCommand("fill -1 99 -1 1 99 1 stone")
            world.server.runCommand("tp @a 0.5 100 0.5 -90 0")
            context.input.lookAt(-90f, 0f)
            context.waitTicks(10)

            val thrown = context.computeOnClient<Throwable?, Nothing> { runCatching { World.draw(box) }.exceptionOrNull() }
            check(thrown is IllegalStateException) {
                "World.draw with no GizmoCollector open should throw an IllegalStateException from Minecraft's Gizmos.addGizmo, " +
                    "and it ${if (thrown == null) "threw nothing, so the shape was dropped silently or a collector was open here" else "threw $thrown"}"
            }

            drawing = true
            try {
                val shot = context.screenshot("gizmo-collector")
                check(near(shot, Role.GOOD.rgb) > 50) { "The screenshot taken through screenshot() shows no green box, so the frame's collector was not open around it" }
            } finally {
                drawing = false
            }
        }
    }
}
