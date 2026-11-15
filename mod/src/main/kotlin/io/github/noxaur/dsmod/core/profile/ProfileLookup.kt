package io.github.noxaur.dsmod.core.profile

import io.github.noxaur.dsmod.core.backend.BackendException
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.text
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/** Where a lookup gets its answers: the backend, or a stub in a test. Each call runs off the client thread. */
interface ProfileSource {
    fun profile(name: String, profile: String?): CompletableFuture<Profile>

    fun container(snapshot: String, id: String): CompletableFuture<Container>
}

/** Something that is being fetched, came back, or failed. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Failed(val failure: Failure) : Load<Nothing>

    data class Loaded<T>(val value: T) : Load<T>
}

/**
 * Why a lookup failed, said to the player. [retry] says a second try may work. [bug] is a failure the mod caused,
 * such as a request the backend could not read, which the screen logs with [cause]. [selected] offers the profile the
 * player plays now, after another was asked for and is gone.
 */
data class Failure(val message: Text, val retry: Boolean, val bug: Boolean = false, val cause: Throwable? = null, val selected: Boolean = false)

/**
 * One player's profile as an open screen shows it. It fetches the profile when made, again on a switch or a retry,
 * and each container the first time it is asked for, all from one snapshot so every tab shows the same moment.
 * Answers come back through [main], which runs them on the client thread, and an answer to a request that a later
 * one replaced is dropped. Nothing polls: the backend caches for every player, so the screen asks only on demand.
 */
class ProfileLookup(val name: String, private val source: ProfileSource, private val main: (() -> Unit) -> Unit) {
    var state: Load<Profile> = Load.Loading
        private set

    /** The profile asked for, or null for the one the player plays now. */
    var asked: String? = null
        private set

    private val containers = HashMap<String, Load<Container>>()
    private var request = 0

    init {
        load(null)
    }

    /** Shows another of the player's profiles. */
    fun switch(profile: String) {
        if ((state as? Load.Loaded)?.value?.profileId != profile) load(profile)
    }

    /** Asks again for what failed: the profile, or else every container that failed. */
    fun retry() {
        if (state is Load.Failed) return load(asked)
        containers.entries.removeAll { it.value is Load.Failed }
    }

    /** Shows the profile the player plays now, after the one asked for was not found. */
    fun selected() = load(null)

    /** The container [id] of the profile shown now, fetched on the first ask. A hidden one is never fetched. */
    fun container(id: String): Load<Container> {
        val profile = (state as? Load.Loaded)?.value ?: return Load.Loading
        return containers.getOrPut(id) {
            fetch(profile, id, retried = false)
            Load.Loading
        }
    }

    private fun load(profile: String?) {
        asked = profile
        val request = ++request
        state = Load.Loading
        containers.clear()
        source.profile(name, profile).whenComplete { answer, error ->
            main {
                if (request == this.request) state = if (error == null) Load.Loaded(answer) else Load.Failed(failure(error))
            }
        }
    }

    private fun fetch(profile: Profile, id: String, retried: Boolean) {
        val request = request
        source.container(profile.snapshot, id).whenComplete { answer, error ->
            main {
                if (request != this.request) return@main
                val e = unwrap(error)
                when {
                    e == null -> containers[id] = Load.Loaded(answer)
                    // A snapshot lasts 10 minutes. A new one shows another moment, so the other tabs fetch theirs again.
                    e is BackendException && e.tag == "SnapshotExpired" && !retried -> renew(id)
                    else -> containers[id] = Load.Failed(failure(error))
                }
            }
        }
    }

    private fun renew(id: String) {
        val request = request
        source.profile(name, (state as? Load.Loaded)?.value?.profileId ?: asked).whenComplete { answer, error ->
            main {
                if (request != this.request) return@main
                if (error != null) {
                    containers[id] = Load.Failed(failure(error))
                    return@main
                }
                state = Load.Loaded(answer)
                containers.clear()
                containers[id] = Load.Loading
                fetch(answer, id, retried = true)
            }
        }
    }

    private fun failure(error: Throwable): Failure {
        val e = unwrap(error)
        if (e !is BackendException) return Failure(text { bad("Something went wrong loading this profile.") }, retry = true, bug = true, cause = e)
        return when {
            e.tag == "PlayerNotFound" -> Failure(text { bad("No player called $name.") }, retry = false)
            e.tag == "ProfileNotFound" && asked != null -> Failure(text { bad("$name has no such profile now.") }, retry = false, selected = true)
            e.tag == "ProfileNotFound" -> Failure(text { bad("$name has no profile yet.") }, retry = false)
            e.tag == "UpstreamUnavailable" -> Failure(text { bad("dragsim.co is not answering. Try again soon.") }, retry = true)
            e.status == null -> Failure(text { bad("${e.message} Try again soon.") }, retry = true)
            e.status == 401 -> Failure(text { bad("ds-mod could not sign you in. Try again soon.") }, retry = true)
            e.status >= 500 -> Failure(text { bad("ds-mod's backend is down. Try again soon.") }, retry = true)
            else -> Failure(text { bad("Something went wrong loading this profile.") }, retry = true, bug = true, cause = e)
        }
    }

    private fun unwrap(error: Throwable?) = (error as? CompletionException)?.cause ?: error
}
