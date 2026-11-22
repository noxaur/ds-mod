package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.Sighting
import io.github.noxaur.dsmod.core.capture.PlayerNames
import io.github.noxaur.dsmod.core.capture.Redactor
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SystemMessage

class Unknowns(private val own: () -> Set<String>) {
    val batch = Batch<Sighting> { it.kind to it.signature }

    fun see(kind: String, line: String) {
        if (batch.full || !batch.fresh(kind to line)) return
        // @keep Redacted as a chat line, so that what a player typed is replaced as well as their name.
        val names = PlayerNames.inText(line).mapTo(HashSet(own()), line::substring)
        val redacted = (Redactor(names).redact(SystemMessage(ServerText(listOf(ServerSpan(line))))) as SystemMessage).text.plain
        val sample = redacted.take(MAX_SAMPLE)
        val signature = SPACES.replace(SIGNATURE_DIGITS.replace(NAME_ALIAS.replace(redacted, "<player>"), "#"), " ")
            .trim().take(MAX_SIGNATURE)
        batch.add(Sighting(kind.take(MAX_KIND), signature, sample))
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
