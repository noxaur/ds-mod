package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.boss.BossBeacon
import io.github.noxaur.dsmod.core.feature.boss.BossTracer
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.EntityBeam
import io.github.noxaur.dsmod.core.world.Mark
import io.github.noxaur.dsmod.core.world.Tracer
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.gizmos.Gizmos
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * An iron golem named as dragsim.co names the Endstone Protector stands 12 blocks north, and a zombie named as a
 * slayer boss 12 blocks east. The zombie comes right after `☠ SLAYER BOSS SPAWNING!`, so it is the player's own. The
 * `BossPoller` reports both, and the golem's death. Boss Beacon puts a beam on both, which follow them, and Boss Tracer
 * a line to each, which still shows with the player turned away. Read the screenshots: two labeled beams with their distances, two lines from the crosshair, one off
 * each edge of the screen, and the settings with the bosses each feature marks.
 */
class BossMarksTest : FabricClientGameTest {
    private fun marks(context: ClientGameTestContext, id: String) = context.computeOnClient<List<Mark>, Nothing> {
        DsModClient.game.readouts.single { it.feature.id == id }.marks
    }

    /**
     * Takes a screenshot with the marks drawn on each frame in it. Fabric's screenshot draws a frame outside the game's
     * frame loop, where no collector for the frame's gizmos is open, so one is opened around it.
     */
    private fun screenshot(context: ClientGameTestContext, name: String): Path {
        val frame = context.computeOnClient<Gizmos.TemporaryCollection, Nothing> { it.levelExtractor.collectPerFrameMainThreadGizmos() }
        try {
            return context.takeScreenshot(name)
        } finally {
            context.runOnClient<Nothing> { frame.close() }
        }
    }

    /** How many pixels of [screenshot] are near [rgb]. */
    private fun near(screenshot: Path, rgb: Int): Int {
        val image = ImageIO.read(screenshot.toFile())
        var count = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val pixel = image.getRGB(x, y)
            val distance = Math.abs((pixel shr 16 and 0xFF) - (rgb shr 16 and 0xFF)) +
                Math.abs((pixel shr 8 and 0xFF) - (rgb shr 8 and 0xFF)) + Math.abs((pixel and 0xFF) - (rgb and 0xFF))
            if (distance < 60) count++
        }
        return count
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            context.runOnClient<Nothing> {
                DsModClient.feature<BossBeacon>().enabled = true
                DsModClient.feature<BossTracer>().enabled = true
            }
            world.server.runCommand("time set noon")
            world.server.runCommand("""execute at @a run summon iron_golem ~ ~ ~-12 {NoAI:1b,CustomName:"[Lv100] Endstone Protector 10M/10M"}""")
            context.runOnClient<Nothing> {
                DsModClient.game.post(System.currentTimeMillis(), SystemMessage(ServerText(ServerSpan("☠ SLAYER BOSS SPAWNING!"))))
            }
            world.server.runCommand("""execute at @a run summon zombie ~12 ~ ~ {NoAI:1b,CustomName:"☠ Revenant Horror I 3.8M/3.8M❤"}""")
            context.waitTicks(10)

            val beams = marks(context, "boss-beacon")
            check(beams.map { (it as EntityBeam).label }.sorted() == listOf("Endstone Protector", "Revenant Horror I")) { "The beams are $beams" }
            check(marks(context, "boss-tracer").size == 2 && marks(context, "boss-tracer").all { it is Tracer }) { "The tracers are ${marks(context, "boss-tracer")}" }

            // Halfway between the two, so both beams are in view.
            context.input.lookAt(-135f, 0f)
            context.waitTicks(5)
            screenshot(context, "boss-beacon")

            context.runOnClient<Nothing> { DsModClient.feature<BossBeacon>().enabled = false }
            context.input.lookAt(45f, 0f)
            context.waitTicks(5)
            val away = screenshot(context, "boss-tracer")
            val color = Role.BAD.rgb
            check(near(away, color) > 200) { "Looking away, the screenshot shows no tracer" }

            // Another player's slayer boss is left out unless the player asks for it.
            world.server.runCommand("""execute at @a run summon zombie ~-12 ~ ~ {NoAI:1b,CustomName:"☠ Atoned Horror V 50M/50M❤"}""")
            context.waitTicks(10)
            check(marks(context, "boss-tracer").size == 2) { "Another player's slayer boss is traced: ${marks(context, "boss-tracer")}" }
            context.runOnClient<Nothing> { DsModClient.feature<BossTracer>().otherSlayers.value = true }
            context.waitTicks(10)
            check(marks(context, "boss-tracer").size == 3) { "Other Slayer Bosses traces no third boss: ${marks(context, "boss-tracer")}" }

            world.server.runCommand("kill @e[type=iron_golem]")
            context.waitTicks(10)
            check(marks(context, "boss-tracer").size == 2) { "The dead Protector is still traced: ${marks(context, "boss-tracer")}" }

            context.runOnClient<Nothing> { DsModClient.openSettings("tracerOtherSlayers") }
            context.waitTicks(20)
            context.takeScreenshot("boss-settings")
            context.setScreen { null }
        }
    }
}
