package io.github.noxaur.dsmod.core.profile

import io.github.noxaur.dsmod.core.backend.BackendException
import io.github.noxaur.dsmod.core.backend.unwrapped
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.text
import java.util.concurrent.CompletableFuture

interface ProfileSource {
    fun profile(name: String, profile: String?): CompletableFuture<Profile>

    fun container(snapshot: String, id: String): CompletableFuture<Container>

    fun containers(snapshot: String): CompletableFuture<List<Container>>
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Failed(val failure: Failure) : Load<Nothing>

    data class Loaded<T>(val value: T) : Load<T>
}

data class Failure(val message: Text, val retry: Boolean, val bug: Boolean = false, val cause: Throwable? = null, val selected: Boolean = false)

class ProfileLookup(val name: String, private val source: ProfileSource, private val main: (() -> Unit) -> Unit) {
    var state: Load<Profile> = Load.Loading
        private set

    var asked: String? = null
        private set

    private val containers = HashMap<String, Load<Container>>()
    private var all: Load<List<Container>>? = null
    private var request = 0

    init {
        load(null)
    }

    fun switch(profile: String) {
        if ((state as? Load.Loaded)?.value?.profileId != profile) load(profile)
    }

    /** @keep For as long as the backend keeps an answer, the profile it calls selected can be the one the player just left,
     * so a lookup that knows the player's profile asks for it by name */
    fun follow(profile: String?) {
        val loaded = (state as? Load.Loaded)?.value ?: return
        loaded.choices.firstOrNull { it.name.plain == profile }?.let { switch(it.id) }
    }

    fun retry() {
        if (state is Load.Failed) return load(asked)
        containers.entries.removeAll { it.value is Load.Failed }
        if (all is Load.Failed) all = null
    }

    fun selected() = load(null)

    fun container(id: String): Load<Container> {
        val profile = (state as? Load.Loaded)?.value ?: return Load.Loading
        return containers.getOrPut(id) {
            fetch({ source.container(it, id) }, { containers[id] = it }, profile.snapshot, retried = false)
            Load.Loading
        }
    }

    // @keep Every container in one answer, for the item overview, which needs them all at once.
    fun containers(): Load<List<Container>> {
        val profile = (state as? Load.Loaded)?.value ?: return Load.Loading
        return all ?: Load.Loading.also {
            all = it
            fetch(source::containers, { all = it }, profile.snapshot, retried = false)
        }
    }

    private fun load(profile: String?) {
        asked = profile
        val request = ++request
        state = Load.Loading
        containers.clear()
        all = null
        source.profile(name, profile).whenCurrent(request, { state = Load.Loaded(it) }) { state = Load.Failed(failure(it)) }
    }

    private fun <T> fetch(ask: (String) -> CompletableFuture<T>, put: (Load<T>) -> Unit, snapshot: String, retried: Boolean) {
        ask(snapshot).whenCurrent(request, { put(Load.Loaded(it)) }) { error ->
            val e = error.unwrapped()
            // @keep A snapshot lasts 10 minutes. A new one shows another moment, so the other tabs fetch theirs again.
            if (e is BackendException && e.tag == "SnapshotExpired" && !retried) renew(ask, put) else put(Load.Failed(failure(e)))
        }
    }

    private fun <T> renew(ask: (String) -> CompletableFuture<T>, put: (Load<T>) -> Unit) {
        source.profile(name, (state as? Load.Loaded)?.value?.profileId ?: asked).whenCurrent(request, { answer ->
            /** @keep The new snapshot replaces the old, so what is still on its way from the old one, or from a second
             * renewal another tab started, is dropped. */
            request++
            state = Load.Loaded(answer)
            containers.clear()
            all = null
            put(Load.Loading)
            fetch(ask, put, answer.snapshot, retried = true)
        }) { put(Load.Failed(failure(it))) }
    }

    private fun <T> CompletableFuture<T>.whenCurrent(request: Int, answered: (T) -> Unit, failed: (Throwable) -> Unit) {
        whenComplete { answer, error ->
            main { if (request == this@ProfileLookup.request) if (error == null) answered(answer) else failed(error) }
        }
    }

    private fun failure(error: Throwable): Failure {
        val e = error.unwrapped()
        if (e !is BackendException) return Failure(text { bad("Something went wrong loading this profile.") }, retry = true, bug = true, cause = e)
        return when {
            e.tag == "PlayerNotFound" -> Failure(text { bad("No player called $name.") }, retry = false)
            e.tag == "ProfileNotFound" && asked != null -> Failure(text { bad("$name has no such profile now.") }, retry = false, selected = true)
            e.tag == "ProfileNotFound" -> Failure(text { bad("$name has no profile yet.") }, retry = false)
            e.tag == "UpstreamUnavailable" -> Failure(text { bad("dragsim.co is not answering. Try again soon.") }, retry = true)
            e.tag == "TooManyRequests" -> Failure(text { bad("That was a lot of profiles at once. Try again in a minute.") }, retry = true)
            e.status == null -> Failure(text { bad("${e.message} Try again soon.") }, retry = true)
            e.status == 401 -> Failure(text { bad("ds-mod could not sign you in. Try again soon.") }, retry = true)
            e.status >= 500 -> Failure(text { bad("ds-mod's backend is down. Try again soon.") }, retry = true)
            else -> Failure(text { bad("Something went wrong loading this profile.") }, retry = true, bug = true, cause = e)
        }
    }
}
