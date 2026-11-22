package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.BoxStyle
import io.github.noxaur.dsmod.edge.World
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.minecraft.world.phys.AABB

class GizmoCollectorTest : FabricClientGameTest {
    @Volatile
    private var drawing = false

    private val box = World.FrameBox(AABB(6.0, 100.6, -0.5, 8.0, 102.6, 1.5), Role.GOOD, BoxStyle.OUTLINE, 4f, true, null)


    override fun runTest(context: ClientGameTestContext) {
        LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register { _, _ -> if (drawing) World.draw(box) }
        context.worldBuilder().create().use { world ->
            world.standOnStone()
            context.input.lookAt(-90f, 0f)
            context.waitTicks(10)

            drawing = true
            try {
                val shot = context.screenshot("gizmo-collector")
                check(countNear(shot, Role.GOOD.rgb, middle = true) > 50) { "The screenshot taken through screenshot() shows no green box, so the frame's collector was not open around it" }
            } finally {
                drawing = false
            }
        }
    }
}
