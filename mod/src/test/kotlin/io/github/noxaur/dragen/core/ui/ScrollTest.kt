package io.github.noxaur.dragen.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrollTest {
    private fun scroll(content: Int = 500, view: Int = 200) = Scroll().also {
        it.view = view
        it.content = content
    }

    @Test
    fun `it stays between the top and the end`() {
        val scroll = scroll()
        scroll.by(-40)
        assertEquals(0, scroll.offset)
        scroll.by(1000)
        assertEquals(300, scroll.offset)
        scroll.content = 250
        assertEquals(50, scroll.offset, "content that shrinks pulls the end up")
        scroll.content = 100
        assertEquals(0, scroll.offset)
        assertFalse(scroll.overflows)
    }

    @Test
    fun `a glide gets there over a few steps, and a wheel turn during one adds to where it is heading`() {
        val scroll = scroll()
        scroll.glide(200)
        assertTrue(scroll.gliding)
        assertTrue(scroll.step(16))
        assertTrue(scroll.offset in 1 until 200)
        repeat(40) { scroll.step(16) }
        assertEquals(200, scroll.offset)
        assertFalse(scroll.gliding)
        scroll.glide(0)
        scroll.step(16)
        scroll.by(20)
        assertEquals(20, scroll.offset, "a jump ends the glide")
    }

    @Test
    fun `the thumb is as tall as the view's share of the content, and at least 8`() {
        val scroll = scroll()
        assertEquals(80, scroll.thumb(200))
        assertEquals(0, scroll.thumbY(200))
        scroll.to(300)
        assertEquals(120, scroll.thumbY(200))
        assertEquals(8, scroll(content = 100_000).thumb(200))
        assertEquals(200, scroll(content = 100).thumb(200), "content that fits fills the track")
    }

    @Test
    fun `dragging the thumb scrolls by the share of the track it moved`() {
        val scroll = scroll()
        scroll.drag(60.0, 200)
        assertEquals(150, scroll.offset)
        scroll.drag(500.0, 200)
        assertEquals(300, scroll.offset)
    }
}
