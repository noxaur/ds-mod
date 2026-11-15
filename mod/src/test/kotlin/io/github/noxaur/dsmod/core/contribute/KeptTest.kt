package io.github.noxaur.dsmod.core.contribute

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeptTest {
    private val dir = Files.createTempDirectory("kept")

    @Test
    fun `a kept name gives back its request`() {
        val file = dir.resolve(Kept.name("req-1", "2026-11-15T10-00-00Z.jsonl"))
        assertEquals("req-1", Kept.requestId(file))
        assertNull(Kept.requestId(dir.resolve("2026-11-15T10-00-00Z.jsonl.gz")))
        assertNull(Kept.requestId(dir.resolve("req-1~x.jsonl")))
    }

    @Test
    fun `a sweep deletes raw captures and half-written files, and keeps redacted recordings`() {
        val kept = dir.resolve(Kept.name("r1", "a.jsonl")).also { it.writeText("gz") }
        val raw = dir.resolve("b.jsonl").also { it.writeText("Alice: hi") }
        val part = dir.resolve(Kept.name("r1", "c.jsonl") + ".part").also { it.writeText("x") }

        assertEquals(listOf(kept), Kept.waiting(dir))
        assertTrue(Files.exists(raw) && Files.exists(part), "listing deletes nothing")

        Kept.sweep(dir)
        assertFalse(Files.exists(raw) || Files.exists(part))
        assertEquals(listOf(kept), Kept.waiting(dir))
        assertEquals(emptyList(), Kept.waiting(dir.resolve("missing")))
    }

    @Test
    fun `a file that will not go does not keep the others`() {
        dir.resolve("a-stuck").also { Files.createDirectory(it) }.resolve("inside").writeText("x")
        val raw = dir.resolve("b.jsonl").also { it.writeText("Alice: hi") }

        kotlin.test.assertFailsWith<java.io.IOException> { Kept.sweep(dir) }
        assertFalse(Files.exists(raw))
    }
}
