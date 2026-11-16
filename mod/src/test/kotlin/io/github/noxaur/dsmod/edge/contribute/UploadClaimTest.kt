package io.github.noxaur.dsmod.edge.contribute

import io.github.noxaur.dsmod.core.backend.Upload
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadClaimTest {
    @Test
    fun `a sent recording stays claimed when the file cannot be deleted`() {
        val dir = Files.createTempDirectory("dsmod-sent")
        Files.createTempFile(dir, "still", ".bin")
        val seen = mutableListOf<String>()
        val release = Contribute.settle(Upload.SENT, dir) {
            seen += if (Files.exists(dir)) "told-while-kept" else "told-after-delete"
        }
        assertEquals(listOf("told-while-kept"), seen)
        assertTrue(Files.exists(dir))
        assertFalse(release)
    }

    @Test
    fun `a refused recording is deleted before the player is told`() {
        val file = Files.createTempFile("dsmod-refused", ".gz")
        val seen = mutableListOf<String>()
        val release = Contribute.settle(Upload.CLOSED, file) {
            seen += if (Files.exists(file)) "told-while-kept" else "told-after-delete"
        }
        assertEquals(listOf("told-after-delete"), seen)
        assertFalse(Files.exists(file))
        assertTrue(release)
    }

    @Test
    fun `a refused recording stays claimed when the file cannot be deleted`() {
        val dir = Files.createTempDirectory("dsmod-refused")
        Files.createTempFile(dir, "still", ".bin")
        val seen = mutableListOf<Upload>()
        val release = Contribute.settle(Upload.CLOSED, dir) { seen += it }
        assertEquals(listOf(Upload.CLOSED), seen)
        assertTrue(Files.exists(dir))
        assertFalse(release)
    }
}
