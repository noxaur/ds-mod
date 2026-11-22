package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.harness.chat
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.end.ZealotTracker
import io.github.noxaur.dsmod.core.game.TabList
import java.nio.file.Files
import java.util.concurrent.Executors
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

    private fun game() = object : Harness() {}.also { session ->
        session.tree.features.forEach { it.enabled = true }
        session.post(TabList(lines("Area: The End")))
    }

    private fun Harness.drop(line: String) = post(chat(line))

    private fun Harness.saved() = readout(tree.feature<ZealotTracker>()).feature.state!!.encode()

    @Test
    fun `a saved state comes back in the next session`() {
        val first = game()
        val files = SavedFiles(dir, first.game)
        first.drop("RARE DROP! (Summoning Eye) (✯ 90%)")
        first.drop("RNGESUS DROP! [Lvl 1] Enderman")
        files.close()
        assertEquals("""{"drops":{"Summoning Eye":1,"[Lvl 1] Enderman":1},"kills":0}""", file.readText())
        assertEquals(emptyList(), dir.listDirectoryEntries().filter { it.fileName.toString().endsWith(".tmp") })

        val second = game()
        SavedFiles(dir, second.game).load()
        assertEquals(first.saved(), second.saved())
        assertEquals(emptyMap(), second.game.metrics.snapshot().failures)
    }

    @Test
    fun `a file that does not parse is kept aside and the feature starts from its default`() {
        file.writeText("{not json")
        val session = game()
        val files = SavedFiles(dir, session.game)
        files.load()

        assertEquals("""{"drops":{},"kills":0}""", session.saved())
        assertTrue("zealot-tracker.load" in session.game.metrics.snapshot().failures)
        assertFalse(file.exists())
        assertEquals("{not json", dir.listDirectoryEntries("zealot-tracker.json.corrupt-*").single().readText())

        session.drop("RARE DROP! (Summoning Eye) (✯ 90%)")
        files.close()
        assertEquals("""{"drops":{"Summoning Eye":1},"kills":0}""", file.readText())
    }

    @Test
    fun `a write that failed is tried again`() {
        val blocked = dir.resolve("blocked")
        blocked.writeText("a file where the folder should be")
        val session = game()
        val writer = Executors.newSingleThreadExecutor()
        val files = SavedFiles(blocked, session.game) { writer }
        session.drop("RARE DROP! (Summoning Eye) (✯ 90%)")
        files.flush()
        writer.submit {}.get()
        files.flush()
        assertTrue("zealot-tracker.save" in session.game.metrics.snapshot().failures)
        writer.submit {}.get()

        Files.delete(blocked)
        files.close()
        assertEquals("""{"drops":{"Summoning Eye":1},"kills":0}""", blocked.resolve("zealot-tracker.json").readText())
    }

    @Test
    fun `nothing is written when nothing changed`() {
        val session = game()
        val files = SavedFiles(dir, session.game)
        files.load()
        session.drop("hello")
        session.drop("[VIP] Player1: RARE DROP! (Summoning Eye) (✯ 90%)")
        files.close()
        // The tab list in `game()` is the one change, and it is the player's, not a feature's.
        assertEquals(listOf("places.json"), dir.listDirectoryEntries().map { it.fileName.toString() })
    }

    @Test
    fun `a tab list or sidebar that names no new place writes nothing`() {
        val session = game()
        SavedFiles(dir, session.game).close()
        Files.delete(dir.resolve("places.json"))

        val files = SavedFiles(dir, session.game)
        repeat(3) { session.post(TabList(lines("Area: The End"))) }
        files.close()
        assertFalse(dir.resolve("places.json").exists())
    }

    @Test
    fun `the places the player has been come back in the next session`() {
        val first = game()
        first.post(TabList(lines("Area: Hub")))
        SavedFiles(dir, first.game).close()
        assertEquals("""{"names":["The End","Hub"]}""", dir.resolve("places.json").readText())

        val second = Game(SettingsTree().features)
        SavedFiles(dir, second).load()
        assertEquals(setOf("The End", "Hub"), second.places.value.names)
    }

    @Test
    fun `a state is written once per change`() {
        val session = game()
        val files = SavedFiles(dir, session.game)
        session.drop("RARE DROP! (Summoning Eye) (✯ 90%)")
        files.flush()
        files.flush()
        files.close()
        val saved = file.readText()
        file.writeText("changed by someone else")
        files.flush()
        assertEquals("changed by someone else", file.readText())
        assertEquals("""{"drops":{"Summoning Eye":1},"kills":0}""", saved)
    }
}
