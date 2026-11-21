package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.BackendException
import io.github.noxaur.dsmod.core.backend.Sighting
import java.util.concurrent.CompletionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnknownsTest {
    private val unknowns = Unknowns { setOf("Steve") }

    @Test
    fun `lines of one shape are one sighting with one example`() {
        unknowns.see("protector-status", "Rising (Tier 3/5, 97) extra")
        unknowns.see("protector-status", "Rising (Tier 4/5, 12) extra")

        assertEquals(
            listOf(Sighting("protector-status", "Rising (Tier #/#, #) extra", "Rising (Tier 3/5, 97) extra")),
            unknowns.batch.take(),
        )
    }

    @Test
    fun `names are removed from the sample and the signature`() {
        unknowns.see("boss-fight-tracker", "1st Damager [MVP+] Dave - 113.5M")
        unknowns.see("boss-fight-tracker", "Steve got a thing")

        val sightings = unknowns.batch.take()
        assertFalse(sightings.any { "Dave" in it.sample || "Dave" in it.signature || "Steve" in it.sample || "Steve" in it.signature }, sightings.toString())
        assertTrue(sightings.all { "Player" in it.sample })
    }

    @Test
    fun `a shape that was sent is not collected again, and a batch is capped`() {
        unknowns.see("a", "x 1")
        assertEquals(1, unknowns.batch.take().size)
        unknowns.see("a", "x 2")
        assertFalse(unknowns.batch.waiting)

        repeat(200) { unknowns.see("b", "line $it word${"x".repeat(it)}") }
        assertEquals(50, unknowns.batch.take().size)
    }

    @Test
    fun `a sighting fits what the backend accepts`() {
        unknowns.see("k".repeat(100), "word ".repeat(3000))
        val sighting = unknowns.batch.take().single()
        assertTrue(sighting.kind.length <= 64 && sighting.signature.length <= 256 && sighting.sample.length <= 4096)
    }

    @Test
    fun `a batch that was not delivered waits for the next report, and a delivered one is done`() {
        unknowns.see("a", "x 1")
        val batch = unknowns.batch.take()
        unknowns.see("a", "x 2")
        assertFalse(unknowns.batch.waiting, "a shape on its way is not collected twice")

        unknowns.batch.finished(batch, RuntimeException())
        assertEquals(batch, unknowns.batch.take())

        unknowns.batch.finished(batch, null)
        unknowns.see("a", "x 3")
        assertFalse(unknowns.batch.waiting)
    }

    @Test
    fun `a batch the backend refused for good is dropped, and any other failure is tried again`() {
        for (status in listOf(400, 404, 409, 413, 422)) {
            val refused = Unknowns { emptySet() }
            refused.see("a", "x $status")
            refused.batch.finished(refused.batch.take(), CompletionException(BackendException(status, "refused")))
            refused.see("a", "x $status")
            assertFalse(refused.batch.waiting, "$status is not tried again")
        }
        for (error in listOf(BackendException(401, "sign in"), BackendException(500, "down"), BackendException(503, "busy"), BackendException(null, "offline"), RuntimeException())) {
            val failed = Unknowns { emptySet() }
            failed.see("a", "x")
            failed.batch.finished(failed.batch.take(), CompletionException(error))
            assertTrue(failed.batch.waiting, "$error is tried again")
        }
    }

    @Test
    fun `batches that keep failing never hold more than one batch`() {
        repeat(5) { round ->
            repeat(200) { unknowns.see("b", "round $round line ${"x".repeat(it)}") }
            val batch = unknowns.batch.take()
            repeat(200) { unknowns.see("c", "round $round line ${"y".repeat(it)}") }
            unknowns.batch.finished(batch, RuntimeException())
        }

        assertEquals(50, unknowns.batch.take().size)
        assertFalse(unknowns.batch.waiting)
    }

    @Test
    fun `a line already read costs a lookup, and no new redactor`() {
        var asked = 0
        val unknowns = Unknowns { asked++; setOf("Steve") }

        repeat(500) { unknowns.see("item-rarity", "MYTHIC-ISH SWORD") }
        assertEquals(1, asked, "only the first sight of a line is redacted")

        unknowns.batch.take()
        repeat(500) { unknowns.see("item-rarity", "MYTHIC-ISH SWORD") }
        assertEquals(1, asked)
        assertFalse(unknowns.batch.waiting)
    }

    @Test
    fun `a player who switches the report off sends nothing that was waiting`() {
        unknowns.see("a", "x 1")
        unknowns.batch.drop()
        assertFalse(unknowns.batch.waiting)
        unknowns.see("a", "x 1")
        assertTrue(unknowns.batch.waiting, "switched on again, the line is read again")
    }

    @Test
    fun `the first report goes two minutes after something began to wait, and later ones are twenty minutes apart`() {
        assertFalse(unknowns.batch.due(0), "nothing waits")

        unknowns.see("a", "x 1")
        assertFalse(unknowns.batch.due(10 * 60_000L))
        assertFalse(unknowns.batch.due(10 * 60_000L + 119_999))
        assertTrue(unknowns.batch.due(10 * 60_000L + 120_000))
        unknowns.batch.take()

        unknowns.see("b", "y 1")
        assertFalse(unknowns.batch.due(10 * 60_000L + 120_000 + 19 * 60_000L))
        assertTrue(unknowns.batch.due(10 * 60_000L + 120_000 + 20 * 60_000L))
    }

    @Test
    fun `a name the player's tab list shows is removed from a sample`() {
        val unknowns = Unknowns { setOf("Steve", "Zed") }
        unknowns.see("boss-fight-tracker", "Zed got a thing")
        assertEquals("Player1 got a thing", unknowns.batch.take().single().sample)
    }

    @Test
    fun `what a player typed is removed with their name, though nothing else names them`() {
        unknowns.see("chat", "Party > Zed: meet me at the portal")
        unknowns.see("chat", "[MVP+] Ann: my secret")
        unknowns.see("chat", "Bea whispers to you: a secret plan")
        unknowns.see("chat", "Party members (2)\n[DRAGON] Cyd: the code is 1234")

        val samples = unknowns.batch.take().map { it.sample }
        assertEquals(4, samples.size)
        assertTrue(samples.none { listOf("Zed", "Ann", "Bea", "Cyd", "portal", "secret", "code").any(it::contains) }, samples.toString())
    }
}
