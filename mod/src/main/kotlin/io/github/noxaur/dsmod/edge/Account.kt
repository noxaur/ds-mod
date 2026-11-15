package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.BackendException
import io.github.noxaur.dsmod.core.backend.Identity
import io.github.noxaur.dsmod.core.text.text
import net.minecraft.client.Minecraft
import java.net.URLEncoder
import java.util.concurrent.CompletionException

/**
 * The player's ds-mod account, which is their Minecraft account. [backend] is the one client every feature that talks
 * to the backend shares, so the mod signs in once. Nothing contacts the backend until the player asks for something.
 */
object Account {
    private val identity = object : Identity {
        override val name: String get() = Minecraft.getInstance().user.name

        override fun join(serverId: String) {
            val client = Minecraft.getInstance()
            val user = client.user
            // The same call the game makes when it joins an online server. The access token goes to Mojang and nowhere else.
            client.services().sessionService().joinServer(user.profileId, user.accessToken, serverId)
        }
    }

    val backend by lazy { Backend(Build.backend, identity) }

    /** `/dsmod login`: trades the session for a link that signs the website in once, and opens it. The token never reaches chat or a log. */
    fun login() {
        Chat.send(text { body("Signing in to the website.") })
        backend.async { Browser.open(Build.website + "/login#" + URLEncoder.encode(oneTimeToken(), Charsets.UTF_8)) }
            .whenComplete { opened, error ->
                val answer = when {
                    error != null -> text { bad("Could not sign in. " + why(error)) }
                    // The link is only in the address that did not open, and it must not reach chat, so the player asks again.
                    !opened -> text { bad("Could not open your browser. Type /dsmod login to try again.") }
                    else -> text { good("Your browser is open on your ds-mod account.") }
                }
                Minecraft.getInstance().execute { Chat.send(answer) }
            }
    }

    private fun why(error: Throwable): String {
        val e = (error as? CompletionException)?.cause ?: error
        if (e !is BackendException) return "Something went wrong."
        return when (e.status) {
            null -> e.message.orEmpty()
            401 -> "The backend would not sign you in."
            503 -> "Mojang did not answer. Try again in a moment."
            else -> "The backend answered ${e.status}."
        }
    }
}
