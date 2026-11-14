package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.ServerText
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedFilesTest {
    private val dir = Files.createTempDirectory("dsmod-saved")
    private val file = dir.resolve("zealot-tracker.json")

    private fun game() = Game(features(DsModConfig())).also { game -> game.readouts.forEach { it.feature.enabled = true } }

    private fun Game.drop(line: String) = post(0, SystemMessage(ServerText(ServerSpan(line))))

    private fun Game.saved() = readouts.single { it.feature.id == "zealot-tracker" }.feature.state!!.encode()

    @Test
    fun `a saved state comes back in the next session`() {
        val first = game()
        val files = SavedFiles(dir, first)
        first.drop("RARE DROP! (Summoning Eye) (✯ 90%)")
        first.drop("RNGESUS DROP! [Lvl 1] Enderman")
        files.close()
        assertEquals("""{"drops":{"Summoning Eye":1,"[Lvl 1] Enderman":1}}""", file.readText())
        assertEquals(emptyList(), dir.listDirectoryEntries().filter { it.fileName.toString().endsWith(".tmp") })

        val second = game()
        SavedFiles(dir, second).load()
        assertEquals(first.saved(), second.saved())
        assertEquals(emptyMap(), second.metrics.snapshot().failures)
    }

    @Test
    fun `a file that does not parse is kept aside and the feature starts from its default`() {
        file.writeText("{not json")
        val game = game()
        val files = SavedFiles(dir, game)
        files.load()

        assertEquals("""{"drops":{}}""", game.saved())
        assertTrue("zealot-tracker.load" in game.metrics.snapshot().failures)
        assertFalse(file.exists())
        assertEquals("{not json", dir.listDirectoryEntries("zealot-tracker.json.corrupt-*").single().readText())

        game.drop("RARE DROP! (Summoning Eye) (✯ 90%)")
        files.close()
        assertEquals("""{"drops":{"Summoning Eye":1}}""", file.readText())
    }

    @Test
    fun `nothing is written when nothing changed`() {
        val game = game()
        val files = SavedFiles(dir, game)
        files.load()
        game.drop("hello")
        game.drop("[VIP] Player1: RARE DROP! (Summoning Eye) (✯ 90%)")
        files.close()
        assertEquals(emptyList(), dir.listDirectoryEntries())
    }

    @Test
    fun `a state is written once per change`() {
        val game = game()
        val files = SavedFiles(dir, game)
        game.drop("RARE DROP! (Summoning Eye) (✯ 90%)")
        files.flush()
        files.flush()
        files.close()
        val saved = file.readText()
        file.writeText("changed by someone else")
        files.flush()
        assertEquals("changed by someone else", file.readText())
        assertEquals("""{"drops":{"Summoning Eye":1}}""", saved)
    }
}
