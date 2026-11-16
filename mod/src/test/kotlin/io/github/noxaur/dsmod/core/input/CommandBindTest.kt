package io.github.noxaur.dsmod.core.input

import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.input.CommandBind.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SHIFT = 340
private const val F6 = 295
private const val F7 = 296
private const val W = 87

class CommandBindTest {
    private val everywhere: (Area) -> Boolean = { true }

    @Test
    fun `a slash sends a command and anything else a chat line`() {
        assertEquals(Message.Command("warp end"), CommandBind(command = " /warp end ").message)
        assertEquals(Message.Command("hub"), CommandBind(command = "/ hub").message)
        assertEquals(Message.Chat("gg"), CommandBind(command = "gg").message)
        assertNull(CommandBind(command = "  ").message)
        assertNull(CommandBind(command = " / ").message)
    }

    @Test
    fun `binds clash on the same keys where and in an area both work`() {
        val end = CommandBind(keys = listOf(SHIFT, F6), area = Area.END)
        assertTrue(end.clashesWith(CommandBind(keys = listOf(F6, SHIFT))))
        assertTrue(end.clashesWith(CommandBind(keys = listOf(F6, SHIFT), where = Where.BOTH, area = Area.END)))
        assertFalse(end.clashesWith(CommandBind(keys = listOf(F6, SHIFT), area = Area.VILLAGE)))
        assertFalse(end.clashesWith(CommandBind(keys = listOf(F6, SHIFT), where = Where.MENUS)))
        assertFalse(end.clashesWith(CommandBind(keys = listOf(F6, SHIFT), enabled = false)))
        assertFalse(end.clashesWith(CommandBind(keys = listOf(F6, SHIFT), inOrder = true)))
        assertFalse(CommandBind(keys = listOf(F6, F7), inOrder = true).clashesWith(CommandBind(keys = listOf(F7, F6), inOrder = true)))
        assertFalse(CommandBind().clashesWith(CommandBind()))
    }

    @Test
    fun `keys held together fire on the press that completes them, in either order`() {
        val binds = listOf(CommandBind(command = "/a", keys = listOf(SHIFT, F6)))
        val matcher = BindMatcher()
        assertNull(matcher.press(SHIFT, 0, binds, Where.WORLD, everywhere))
        assertEquals(binds[0], matcher.press(F6, 0, binds, Where.WORLD, everywhere))
        matcher.release(F6)
        assertEquals(binds[0], matcher.press(F6, 0, binds, Where.WORLD, everywhere), "a second tap with Shift still held")
        matcher.release(SHIFT)
        matcher.release(F6)
        assertNull(matcher.press(F6, 0, binds, Where.WORLD, everywhere))
        assertEquals(binds[0], matcher.press(SHIFT, 0, binds, Where.WORLD, everywhere))
    }

    @Test
    fun `another held key stops a bind only when it takes no extra keys`() {
        val loose = CommandBind(command = "/a", keys = listOf(F6))
        val strict = loose.copy(extraKeys = false)
        val matcher = BindMatcher()
        matcher.press(W, 0, emptyList(), Where.WORLD, everywhere)
        assertEquals(loose, matcher.press(F6, 0, listOf(loose), Where.WORLD, everywhere))
        matcher.release(F6)
        assertNull(matcher.press(F6, 0, listOf(strict), Where.WORLD, everywhere))
        matcher.forget { it != W }
        matcher.release(F6)
        assertEquals(strict, matcher.press(F6, 0, listOf(strict), Where.WORLD, everywhere), "once W is up")
    }

    @Test
    fun `the highest priority fires, then the first listed`() {
        val first = CommandBind(command = "/a", keys = listOf(F6))
        val second = first.copy(command = "/b")
        val urgent = first.copy(command = "/c", priority = 2)
        assertEquals(first, BindMatcher().press(F6, 0, listOf(first, second), Where.WORLD, everywhere))
        assertEquals(urgent, BindMatcher().press(F6, 0, listOf(first, second, urgent), Where.WORLD, everywhere))
    }

    @Test
    fun `a bind fires only where it works, in its area, and when on`() {
        val bind = CommandBind(command = "/a", keys = listOf(F6), area = Area.END)
        assertNull(BindMatcher().press(F6, 0, listOf(bind), Where.MENUS, everywhere))
        assertNull(BindMatcher().press(F6, 0, listOf(bind), null, everywhere))
        assertNull(BindMatcher().press(F6, 0, listOf(bind), Where.WORLD) { it == Area.VILLAGE })
        assertEquals(bind, BindMatcher().press(F6, 0, listOf(bind), Where.WORLD) { it == Area.END })
        assertNull(BindMatcher().press(F6, 0, listOf(bind.copy(enabled = false)), Where.WORLD, everywhere))
        val both = bind.copy(where = Where.BOTH, area = null)
        assertEquals(both, BindMatcher().press(F6, 0, listOf(both), Where.MENUS, everywhere))
    }

    @Test
    fun `a sequence fires at once when nothing longer could follow, or after the gap`() {
        val short = CommandBind(command = "/a", keys = listOf(F6, F7), inOrder = true)
        val long = CommandBind(command = "/b", keys = listOf(F6, F7, F6), inOrder = true)
        val matcher = BindMatcher()
        assertNull(matcher.press(F6, 0, listOf(short), Where.WORLD, everywhere))
        assertEquals(short, matcher.press(F7, 100, listOf(short), Where.WORLD, everywhere))

        val both = listOf(short, long)
        assertNull(matcher.press(F6, 1000, both, Where.WORLD, everywhere))
        assertNull(matcher.press(F7, 1100, both, Where.WORLD, everywhere), "F6 F7 could still become F6 F7 F6")
        assertNull(matcher.idle(1200, 250, both, Where.WORLD, everywhere))
        assertEquals(short, matcher.idle(1350, 250, both, Where.WORLD, everywhere))
        assertNull(matcher.idle(2000, 250, both, Where.WORLD, everywhere), "it fired once")

        matcher.press(F6, 3000, both, Where.WORLD, everywhere)
        matcher.press(F7, 3100, both, Where.WORLD, everywhere)
        assertEquals(long, matcher.press(F6, 3200, both, Where.WORLD, everywhere))
    }

    @Test
    fun `a sequence the wrong way round never fires, and chat breaks one`() {
        val bind = CommandBind(command = "/a", keys = listOf(F6, F7), inOrder = true)
        val matcher = BindMatcher()
        matcher.press(F7, 0, listOf(bind), Where.WORLD, everywhere)
        assertNull(matcher.press(F6, 10, listOf(bind), Where.WORLD, everywhere))
        assertNull(matcher.idle(1000, 250, listOf(bind), Where.WORLD, everywhere))
        matcher.press(F6, 2000, listOf(bind), Where.WORLD, everywhere)
        matcher.press(F7, 2010, listOf(bind), null, everywhere)
        assertNull(matcher.idle(3000, 250, listOf(bind), Where.WORLD, everywhere))
    }

    @Test
    fun `a sequence still waiting is dropped when the place no longer covers where it was typed`() {
        val short = CommandBind(command = "/a", keys = listOf(F6, F7), inOrder = true)
        val long = CommandBind(command = "/b", keys = listOf(F6, F7, F6), inOrder = true)
        val both = listOf(short, long)
        val matcher = BindMatcher()
        matcher.press(F6, 1000, both, Where.WORLD, everywhere)
        matcher.press(F7, 1100, both, Where.WORLD, everywhere)
        assertNull(matcher.idle(1350, 250, both, null, everywhere), "chat, a focused field, F3, or Command Binds off")
        assertNull(matcher.idle(2000, 250, both, Where.WORLD, everywhere), "the pending sequence was cleared")

        matcher.press(F6, 3000, both, Where.WORLD, everywhere)
        matcher.press(F7, 3100, both, Where.WORLD, everywhere)
        assertNull(matcher.idle(3200, 250, both, Where.MENUS, everywhere), "a menu does not cover the world")
        assertNull(matcher.idle(4000, 250, both, Where.WORLD, everywhere))

        matcher.press(F6, 5000, both, Where.MENUS, everywhere)
        matcher.press(F7, 5100, both, Where.MENUS, everywhere)
        assertNull(matcher.idle(5200, 250, both, null, everywhere), "dropped before the gap ends")
        assertNull(matcher.idle(6000, 250, both, Where.MENUS, everywhere))
    }
}

class BindShareTest {
    @Test
    fun `a shared group comes back with its binds switched off`() {
        val binds = listOf(
            CommandBind("Warp", "/warp end", listOf(SHIFT, F6), where = Where.BOTH, area = Area.END, priority = 3, group = "Mine"),
            CommandBind("GG", "gg", listOf(F6, F7), inOrder = true, extraKeys = false),
        )
        val shared = BindShare.decode(BindShare.encode("Dungeons", binds))!!
        assertEquals("Dungeons", shared.group)
        assertEquals(binds.map { it.copy(enabled = false, group = "") }, shared.binds)
    }

    @Test
    fun `anything else on the clipboard is not a group`() {
        assertNull(BindShare.decode("hello"))
        assertNull(BindShare.decode("dsmod-binds:not base64!"))
        assertNull(BindShare.decode("dsmod-binds:" + java.util.Base64.getEncoder().encodeToString("plain".toByteArray())))
    }
}
