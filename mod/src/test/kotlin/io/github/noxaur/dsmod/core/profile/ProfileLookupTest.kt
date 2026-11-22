package io.github.noxaur.dsmod.core.profile

import io.github.noxaur.dsmod.harness.resource
import io.github.noxaur.dsmod.core.backend.BackendException
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Drives the lookup with answers held back until the test gives them, so each order of answers can be tried. */
class ProfileLookupTest {
    private val profile = Profile.parse(resource("profiles/profile.json"))
    private val inventory = Profile.parseContainer(resource("profiles/containers/inventory.json"))

    private class Source : ProfileSource {
        val profiles = ArrayList<Pair<String?, CompletableFuture<Profile>>>()
        val containers = ArrayList<Pair<String, CompletableFuture<Container>>>()

        override fun profile(name: String, profile: String?) = CompletableFuture<Profile>().also { profiles += profile to it }

        override fun container(snapshot: String, id: String) = CompletableFuture<Container>().also { containers += "$snapshot/$id" to it }
    }

    private val source = Source()
    private val lookup = ProfileLookup("Testwyrm", source) { it() }

    private fun failed(status: Int?, tag: String? = null) = BackendException(status, "The backend answered $status.", tag = tag)

    @Test
    fun `opening asks for the profile the player plays now`() {
        assertEquals(listOf<String?>(null), source.profiles.map { it.first })
        assertIs<Load.Loading>(lookup.state)
        source.profiles[0].second.complete(profile)
        assertEquals(Load.Loaded(profile), lookup.state)
    }

    @Test
    fun `a container is fetched once, from the profile's snapshot`() {
        source.profiles[0].second.complete(profile)
        assertIs<Load.Loading>(lookup.container("inventory"))
        assertIs<Load.Loading>(lookup.container("inventory"))
        assertEquals(listOf("${profile.snapshot}/inventory"), source.containers.map { it.first })
        source.containers[0].second.complete(inventory)
        assertEquals(Load.Loaded(inventory), lookup.container("inventory"))
    }

    @Test
    fun `nothing is fetched for a container before the profile is in`() {
        assertIs<Load.Loading>(lookup.container("inventory"))
        assertTrue(source.containers.isEmpty())
    }

    @Test
    fun `an answer to a profile the player switched away from is dropped`() {
        source.profiles[0].second.complete(profile)
        lookup.switch("aaaaaaaa-0000-4000-8000-000000000001")
        lookup.switch(profile.profileId)
        val other = profile.copy(profileId = "aaaaaaaa-0000-4000-8000-000000000001")
        source.profiles[2].second.complete(profile)
        source.profiles[1].second.complete(other)
        assertEquals(Load.Loaded(profile), lookup.state)
    }

    @Test
    fun `switching to the profile shown asks for nothing`() {
        source.profiles[0].second.complete(profile)
        lookup.switch(profile.profileId)
        assertEquals(1, source.profiles.size)
    }

    @Test
    fun `an expired snapshot is renewed once, and every tab then shows the new moment`() {
        source.profiles[0].second.complete(profile)
        lookup.container("ender_chest")
        source.containers[0].second.complete(inventory.copy(id = "ender_chest"))
        lookup.container("inventory")
        source.containers[1].second.completeExceptionally(failed(410, "SnapshotExpired"))

        assertEquals(profile.profileId, source.profiles[1].first)
        val renewed = profile.copy(snapshot = "renewed")
        source.profiles[1].second.complete(renewed)
        assertEquals(Load.Loaded(renewed), lookup.state)
        assertEquals("renewed/inventory", source.containers[2].first)
        source.containers[2].second.completeExceptionally(failed(410, "SnapshotExpired"))
        assertIs<Load.Failed>(lookup.container("inventory"))
        // The ender chest was from the old moment, so it is fetched again from the new one.
        assertIs<Load.Loading>(lookup.container("ender_chest"))
        assertEquals("renewed/ender_chest", source.containers.last().first)
    }

    @Test
    fun `two tabs that find the snapshot expired end on one new snapshot`() {
        source.profiles[0].second.complete(profile)
        lookup.container("inventory")
        lookup.container("ender_chest")
        source.containers[0].second.completeExceptionally(failed(410, "SnapshotExpired"))
        source.containers[1].second.completeExceptionally(failed(410, "SnapshotExpired"))
        source.profiles[1].second.complete(profile.copy(snapshot = "new-1"))
        // The second renewal belongs to the old snapshot, so it changes nothing.
        source.profiles[2].second.complete(profile.copy(snapshot = "new-2"))
        assertEquals("new-1", assertIs<Load.Loaded<Profile>>(lookup.state).value.snapshot)
        source.containers.last().second.complete(inventory)
        assertEquals("new-1/inventory", source.containers.last().first)
        assertEquals(Load.Loaded(inventory), lookup.container("inventory"))
        // The ender chest's own renewal was dropped, so it is asked for again from the new snapshot.
        lookup.container("ender_chest")
        assertEquals("new-1/ender_chest", source.containers.last().first)
    }

    @Test
    fun `retry asks again for what failed`() {
        source.profiles[0].second.completeExceptionally(failed(503, "UpstreamUnavailable"))
        val failure = assertIs<Load.Failed>(lookup.state).failure
        assertEquals("dragsim.co is not answering. Try again soon.", failure.message.plain)
        assertTrue(failure.retry)
        lookup.retry()
        assertEquals(2, source.profiles.size)

        source.profiles[1].second.complete(profile)
        lookup.container("inventory")
        source.containers[0].second.completeExceptionally(failed(503, "UpstreamUnavailable"))
        lookup.retry()
        lookup.container("inventory")
        assertEquals(2, source.containers.size)
    }

    @Test
    fun `each failure says what happened`() {
        fun message(error: Throwable, profile: String? = null): Failure {
            val source = Source()
            val lookup = ProfileLookup("Testwyrm", source) { it() }
            if (profile != null) {
                source.profiles[0].second.complete(this.profile)
                lookup.switch(profile)
            }
            source.profiles.last().second.completeExceptionally(error)
            return assertIs<Load.Failed>(lookup.state).failure
        }
        assertEquals("No player called Testwyrm.", message(failed(404, "PlayerNotFound")).message.plain)
        assertEquals("Testwyrm has no profile yet.", message(failed(404, "ProfileNotFound")).message.plain)
        val gone = message(failed(404, "ProfileNotFound"), "aaaaaaaa-0000-4000-8000-000000000001")
        assertEquals("Testwyrm has no such profile now.", gone.message.plain)
        assertTrue(gone.selected)
        assertEquals("ds-mod's backend is down. Try again soon.", message(failed(500)).message.plain)
        assertEquals("The backend could not be reached. Try again soon.", BackendException(null, "The backend could not be reached.").let { message(it) }.message.plain)
        assertTrue(message(failed(400)).bug)
        assertTrue(message(IllegalStateException("not JSON")).bug)
        assertEquals(false, message(failed(404, "PlayerNotFound")).retry)
    }
}
