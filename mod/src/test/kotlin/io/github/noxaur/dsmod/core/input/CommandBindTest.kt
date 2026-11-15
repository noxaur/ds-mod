package io.github.noxaur.dsmod.core.input

import io.github.noxaur.dsmod.core.input.CommandBind.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandBindTest {
    @Test
    fun `a slash sends a command and anything else a chat line`() {
        assertEquals(Message.Command("warp end"), CommandBind(" /warp end ").message)
        assertEquals(Message.Command("hub"), CommandBind("/ hub").message)
        assertEquals(Message.Chat("gg"), CommandBind("gg").message)
    }

    @Test
    fun `a row with nothing to send sends nothing`() {
        assertNull(CommandBind("").message)
        assertNull(CommandBind("   ").message)
        assertNull(CommandBind(" / ").message)
    }

    @Test
    fun `keys bound twice clash, and unbound rows never do`() {
        val binds = listOf(CommandBind("/a", 295), CommandBind("/b", 295), CommandBind("/c", 3), CommandBind("/d"), CommandBind("/e"))
        assertEquals(setOf(295), CommandBind.clashes(binds))
    }
}
