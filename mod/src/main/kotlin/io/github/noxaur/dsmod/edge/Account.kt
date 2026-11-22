package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.BackendException
import io.github.noxaur.dsmod.core.backend.Identity
import io.github.noxaur.dsmod.core.backend.unwrapped
import io.github.noxaur.dsmod.core.text.text
import net.minecraft.client.Minecraft
import java.net.URLEncoder

object Account {
    private val identity = object : Identity {
        override val name: String get() = Minecraft.getInstance().user.name

        override fun join(serverId: String) {
            val client = Minecraft.getInstance()
            val user = client.user
            // @keep The same call the game makes when it joins an online server, so the access token goes to Mojang and nowhere else.
            client.services().sessionService().joinServer(user.profileId, user.accessToken, serverId)
        }
    }

    @Volatile
    private var chosen: Backend? = null

    var backend: Backend
        get() = chosen ?: synchronized(this) { chosen ?: Backend(Build.backend, identity, Build.version).also { chosen = it } }
        set(value) {
            chosen = value
        }

    val name: String get() = identity.name

    fun login() {
        Chat.send(text { body("Signing in to the website.") })
        backend.async { Browser.open(Build.website + "/login#" + URLEncoder.encode(oneTimeToken(), Charsets.UTF_8)) }
            .whenComplete { opened, error ->
                val answer = when {
                    error != null -> text { bad("Could not sign in. " + why(error)) }
                    // @keep The link is only in the address that did not open and must not reach chat, so the player asks again.
                    !opened -> text { bad("Could not open your browser. Type /dsmod login to try again.") }
                    else -> text { good("Your browser is open on your ds-mod account.") }
                }
                Minecraft.getInstance().execute { Chat.send(answer) }
            }
    }

    fun why(error: Throwable): String {
        val e = error.unwrapped()
        if (e !is BackendException) return "Something went wrong."
        return when (e.status) {
            null -> e.message.orEmpty()
            400 -> "The backend could not read what was sent."
            401 -> "The backend would not sign you in."
            404 -> "The backend does not know that request."
            503 -> when (e.message) {
                Backend.MOJANG_DOWN -> "Mojang did not answer. Try again in a moment."
                else -> "The backend did not answer. Try again in a moment."
            }
            else -> "The backend answered ${e.status}."
        }
    }
}
