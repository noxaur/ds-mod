package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.Sighting
import io.github.noxaur.dsmod.core.capture.PlayerNames
import io.github.noxaur.dsmod.core.capture.Redactor
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SystemMessage

/**
 * Collects what reached a feature and failed to parse, for the player who agreed to send it. A sighting is one
 * [Sighting.kind] and one [Sighting.signature], which is the line with its digits and names replaced, so two lines
 * of the same shape are one sighting with one redacted example. [own] gives the names the player surely sees, their
 * own and the tab list's.
 * Call it on the client thread.
 */
class Unknowns(private val own: () -> Set<String>) : Batch<Sighting>({ it.kind to it.signature }) {
    /** Records [line] as something [kind] could not read, unless this shape is already waiting or went out. */
    fun see(kind: String, line: String) {
        if (full || !fresh(kind to line)) return
        // Redacted as a chat line, so that what a player typed is replaced as well as their name.
        val names = PlayerNames.inText(line).mapTo(HashSet(own()), line::substring)
        val redacted = (Redactor(names).redact(SystemMessage(ServerText(listOf(ServerSpan(line))))) as SystemMessage).text.plain
        val sample = redacted.take(MAX_SAMPLE)
        val signature = SPACES.replace(SIGNATURE_DIGITS.replace(NAME_ALIAS.replace(redacted, "<player>"), "#"), " ")
            .trim().take(MAX_SIGNATURE)
        add(Sighting(kind.take(MAX_KIND), signature, sample))
    }

    private companion object {
        const val MAX_KIND = 64
        const val MAX_SIGNATURE = 256
        const val MAX_SAMPLE = 4096
        val SIGNATURE_DIGITS = Regex("""\d+(?:[.,]\d+)*""")
        val NAME_ALIAS = Regex("""Player\d+""")
        val SPACES = Regex("""\s+""")
    }
}
