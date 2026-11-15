package io.github.noxaur.dsmod.edge.contribute

import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.BackendException
import io.github.noxaur.dsmod.core.backend.Identity
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Background
import io.github.noxaur.dsmod.edge.Browser
import io.github.noxaur.dsmod.edge.Build
import io.github.noxaur.dsmod.edge.Chat
import net.minecraft.client.Minecraft
import java.net.URLEncoder
import java.util.concurrent.CompletionException

/**
 * Everything a player does with ds-mod's backend, which is `/dsmod login`. Nothing here contacts the backend until
 * the player uses it, and every call runs on [Backend]'s own thread and answers on the client thread.
 */
object Contribute {
    private val identity = object : Identity {
        override val name: String get() = Minecraft.getInstance().user.name

        override fun join(serverId: String) {
            val client = Minecraft.getInstance()
            val user = client.user
            // The same call the game makes when it joins an online server. The access token goes to Mojang and nowhere else.
            client.services().sessionService().joinServer(user.profileId, user.accessToken, serverId)
        }
    }
    private val backend by lazy { Backend(Build.backend, identity) }

    private fun tell(build: io.github.noxaur.dsmod.core.text.TextBuilder.() -> Unit) = Minecraft.getInstance().execute { Chat.send(text(build)) }

    private fun why(error: Throwable): String {
        val e = (error as? CompletionException)?.cause ?: error
        if (e !is BackendException) return "Something went wrong, and nothing was sent."
        return when (e.status) {
            null -> e.message.orEmpty()
            401 -> "The backend would not sign you in."
            else -> "The backend answered ${e.status}."
        }
    }

    /** Trades the session for a link that signs the website in once, and opens it. The token never reaches chat or a log. */
    fun login() {
        tell { body("Signing in to the website.") }
        backend.async { oneTimeToken() }.thenApplyAsync({ token ->
            Browser.open(Build.website + "/login#" + URLEncoder.encode(token, Charsets.UTF_8))
        }, Background.io).whenComplete { opened, error ->
            when {
                error != null -> tell { bad("Could not sign in. " + why(error)) }
                opened == false -> tell { bad("Could not open your browser. Open ${Build.website} yourself.") }
                else -> tell { good("Your browser is open on your ds-mod account.") }
            }
        }
    }
}
