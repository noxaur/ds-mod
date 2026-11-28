package io.github.noxaur.dragen.debug.zone

import io.github.noxaur.dragen.api.world.BlockArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZonePickTest {
    @Test
    fun `corners in any order make one box that holds both`() {
        val pick = ZonePick()
        pick.set(1, Corner(-615, 30, -255))
        assertNull(pick.area)
        pick.set(2, Corner(-640, 5, -280))
        assertEquals(BlockArea(-640, 5, -280, -615, 30, -255), pick.area)
        assertEquals(Triple(26, 26, 26), ZonePick.size(pick.area!!))
    }

    @Test
    fun `a scroll moves the last set corner along the axis the player looks down most`() {
        val pick = ZonePick()
        assertEquals(false, pick.nudge(0.0, 1.0, 0.0, 1), "There is no corner to move yet.")
        pick.set(1, Corner(0, 10, 0))
        pick.set(2, Corner(5, 10, 5))
        pick.nudge(0.1, 0.9, -0.3, 3)
        assertEquals(Corner(5, 13, 5), pick.second, "Looking up, scrolling raises the corner without flying.")
        pick.nudge(0.2, -0.1, -0.8, -1)
        assertEquals(Corner(5, 13, 6), pick.second, "Scrolling back moves it away from where the player looks.")
        pick.set(1, Corner(0, 10, 0))
        pick.nudge(-0.7, 0.2, 0.1, 1)
        assertEquals(Corner(-1, 10, 0), pick.first)
    }

    @Test
    fun `a constant is a Zone named in upper snake case and says where it was picked`() {
        val area = BlockArea(-640, 5, -280, -615, 30, -255)
        assertEquals("public val DRAGONS_GATE: Zone = Zone(\"Dragon's Gate\", BlockArea(-640, 5, -280, -615, 30, -255)) // The End", ZonePick.constant(" Dragon's Gate ", area, "The End"))
        assertEquals("public val GATE: Zone = Zone(\"Gate\", BlockArea(-640, 5, -280, -615, 30, -255))", ZonePick.constant("Gate", area, null))
    }
}
