package io.github.noxaur.dsmod.core.feature.menus.profilecooldown

import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.player.PlayerData
import io.github.noxaur.dsmod.core.capture.Captured
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.readCapture
import io.github.noxaur.dsmod.harness.text
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileCooldownTest {
    private val capture = readCapture(Path.of("src/test/resources/features/item-overview/profile-switch.jsonl"))
    private val start = capture.first().time

    private class Session : Harness(alone = ProfileCooldown::class) {
        val feature = tree.feature<ProfileCooldown>()
        val shown = ArrayList<Pair<Long, Long?>>()

        fun replay(events: List<Captured>, start: Long) {
            for (captured in events) {
                time = captured.time
                post(captured.event)
                val opened = captured.event as? ContainerOpened ?: continue
                if (opened.title?.plain == PlayerData.PROFILES) shown += (time - start) to feature.left(opened.id, time)
            }
        }

        fun reopen(): Long? {
            post(ContainerOpened(99, text(PlayerData.PROFILES), 72))
            return feature.left(99, time)
        }
    }

    @Test
    fun `each switch starts the 30 seconds, which a refusal that agrees leaves alone`() {
        val session = Session().apply { replay(capture, start) }
        assertEquals(
            listOf(3883L to null, 28595L to null, 29419L to null, 41528L to 18660L, 42222L to 17966L, 47180L to 13008L, 55830L to 4358L, 59442L to 746L, 60150L to 38L),
            session.shown,
        )
        session.time = start + 60822 + PlayerData.COOLDOWN_MILLIS
        assertEquals(0L, session.reopen(), "Ready shows the moment the 30 seconds are up")
        session.pass(ProfileCooldown.READY_MILLIS - 1)
        assertEquals(1 - ProfileCooldown.READY_MILLIS, session.reopen())
        session.pass(1)
        assertNull(session.reopen(), "Ready shows for 5 seconds only")
    }

    @Test
    fun `a refusal names the wait when the mod missed the switch`() {
        val switched = capture.indexOfFirst { it.time - start == 30188L && it.event is ContainerOpened }
        val session = Session().apply { replay(capture.filterIndexed { i, _ -> i != switched }, start) }
        assertEquals(listOf<Pair<Long, Long?>>(41528L to null, 42222L to null), session.shown.subList(3, 5), "no countdown before the server named one")
        assertEquals<Pair<Long, Long?>>(47180L to 13031L, session.shown[5], "the refusal at +43.211s said 17s")
    }
}
