package io.github.noxaur.dsmod.core.game

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DragSimTest {
    @Test
    fun `the address the player typed names dragsim co`() {
        for (address in listOf("dragsim.co", "DragSim.co", "play.dragsim.co", "dragsim.co:25565", "dragsim.co.", " dragsim.co ")) {
            assertTrue(DragSim.hosts(address), address)
        }
    }

    @Test
    fun `any other server, singleplayer and a look-alike do not`() {
        for (address in listOf(null, "", "localhost", "127.0.0.1:25565", "hypixel.net", "notdragsim.co", "dragsim.co.evil.net", "dragsim.com")) {
            assertFalse(DragSim.hosts(address), address.toString())
        }
    }

    private val dragSimAt = setOf(InetAddress.getByName("144.217.180.49"))

    @Test
    fun `a player who typed the IP, or a name of their own for it, is on dragsim co`() {
        val there = InetAddress.getByName("144.217.180.49")
        assertTrue(DragSim.serves("144.217.180.49:25565", there, dragSimAt))
        assertTrue(DragSim.serves("my-dragons.example", there, dragSimAt))
    }

    @Test
    fun `another machine is not, and neither is an IP before the lookup has an answer`() {
        assertFalse(DragSim.serves("10.0.0.5", InetAddress.getByName("10.0.0.5"), dragSimAt))
        assertFalse(DragSim.serves("144.217.180.49", InetAddress.getByName("144.217.180.49"), emptySet()))
        assertTrue(DragSim.serves("dragsim.co", null, emptySet()))
    }
}
