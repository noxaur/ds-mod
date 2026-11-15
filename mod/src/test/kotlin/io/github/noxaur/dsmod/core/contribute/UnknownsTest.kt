package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.Sighting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnknownsTest {
    private val unknowns = Unknowns { setOf("Steve") }

    @Test
    fun `lines of one shape are one sighting with one example`() {
        unknowns.see("golem-status", "Rising (Tier 3/5, 97) extra")
        unknowns.see("golem-status", "Rising (Tier 4/5, 12) extra")

        assertEquals(
            listOf(Sighting("golem-status", "Rising (Tier #/#, #) extra", "Rising (Tier 3/5, 97) extra")),
            unknowns.take(),
        )
    }

    @Test
    fun `names are removed from the sample and the signature`() {
        unknowns.see("boss-drops", "1st Damager [MVP+] Dave - 113.5M")
        unknowns.see("boss-drops", "Steve got a thing")

        val sightings = unknowns.take()
        assertFalse(sightings.any { "Dave" in it.sample || "Dave" in it.signature || "Steve" in it.sample || "Steve" in it.signature }, sightings.toString())
        assertTrue(sightings.all { "Player" in it.sample })
    }

    @Test
    fun `a shape that was sent is not collected again, and a batch is capped`() {
        unknowns.see("a", "x 1")
        assertEquals(1, unknowns.take().size)
        unknowns.see("a", "x 2")
        assertFalse(unknowns.waiting)

        repeat(200) { unknowns.see("b", "line $it word${"x".repeat(it)}") }
        assertEquals(50, unknowns.take().size)
    }

    @Test
    fun `a sighting fits what the backend accepts`() {
        unknowns.see("k".repeat(100), "word ".repeat(3000))
        val sighting = unknowns.take().single()
        assertTrue(sighting.kind.length <= 64 && sighting.signature.length <= 256 && sighting.sample.length <= 4096)
    }

    @Test
    fun `a batch that was not delivered waits for the next report, and a delivered one is done`() {
        unknowns.see("a", "x 1")
        val batch = unknowns.take()
        unknowns.see("a", "x 2")
        assertFalse(unknowns.waiting, "a shape on its way is not collected twice")

        unknowns.finished(batch, delivered = false)
        assertEquals(batch, unknowns.take())

        unknowns.finished(batch, delivered = true)
        unknowns.see("a", "x 3")
        assertFalse(unknowns.waiting)
    }

    @Test
    fun `batches that keep failing never hold more than one batch`() {
        repeat(5) { round ->
            repeat(200) { unknowns.see("b", "round $round line ${"x".repeat(it)}") }
            val batch = unknowns.take()
            repeat(200) { unknowns.see("c", "round $round line ${"y".repeat(it)}") }
            unknowns.finished(batch, delivered = false)
        }

        assertEquals(50, unknowns.take().size)
        assertFalse(unknowns.waiting)
    }

    @Test
    fun `a line already read costs a lookup, and no new redactor`() {
        var asked = 0
        val unknowns = Unknowns { asked++; setOf("Steve") }

        repeat(500) { unknowns.see("rarity-outlines", "MYTHIC-ISH SWORD") }
        assertEquals(1, asked, "only the first sight of a line is redacted")

        unknowns.take()
        repeat(500) { unknowns.see("rarity-outlines", "MYTHIC-ISH SWORD") }
        assertEquals(1, asked)
        assertFalse(unknowns.waiting)
    }

    @Test
    fun `a player who switches the report off sends nothing that was waiting`() {
        unknowns.see("a", "x 1")
        unknowns.drop()
        assertFalse(unknowns.waiting)
        unknowns.see("a", "x 1")
        assertTrue(unknowns.waiting, "switched on again, the line is read again")
    }

    @Test
    fun `the first report goes two minutes after something began to wait, and later ones are twenty minutes apart`() {
        assertFalse(unknowns.due(0), "nothing waits")

        unknowns.see("a", "x 1")
        assertFalse(unknowns.due(10 * 60_000L))
        assertFalse(unknowns.due(10 * 60_000L + 119_999))
        assertTrue(unknowns.due(10 * 60_000L + 120_000))
        unknowns.take()

        unknowns.see("b", "y 1")
        assertFalse(unknowns.due(10 * 60_000L + 120_000 + 19 * 60_000L))
        assertTrue(unknowns.due(10 * 60_000L + 120_000 + 20 * 60_000L))
    }

    @Test
    fun `a name the player's tab list shows is removed from a sample`() {
        val unknowns = Unknowns { setOf("Steve", "Zed") }
        unknowns.see("boss-drops", "Zed got a thing")
        assertEquals("Player1 got a thing", unknowns.take().single().sample)
    }
}
