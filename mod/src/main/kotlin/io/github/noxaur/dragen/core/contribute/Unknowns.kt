package io.github.noxaur.dragen.core.contribute

import io.github.noxaur.dragen.core.backend.Sighting
import io.github.noxaur.dragen.core.capture.PlayerNames
import io.github.noxaur.dragen.core.capture.Redactor
import io.github.noxaur.dragen.core.game.ServerSpan
import io.github.noxaur.dragen.core.game.ServerText
import io.github.noxaur.dragen.core.game.SystemMessage

class Unknowns(private val own: () -> Set<String>) {
    val batch = Batch<Sighting> { it.kind to it.signature }

    fun see(kind: String, line: String) {
        if (batch.full || !batch.fresh(kind to line)) return
        val names = PlayerNames.inText(line).mapTo(HashSet(own()), line::substring)
        val redacted = (Redactor(names).redact(SystemMessage(ServerText(listOf(ServerSpan(line))))) as SystemMessage).text.plain
        val sample = redacted.take(MAX_SAMPLE)
        val signature = SPACES.replace(SIGNATURE_DIGITS.replace(NAME_ALIAS.replace(redacted, "<player>"), "#"), " ")
            .trim().take(MAX_SIGNATURE)
        batch.add(Sighting(kind.take(MAX_KIND), signature, sample))
    }

    internal companion object {
        const val MAX_KIND = 64
        const val MAX_SIGNATURE = 256
        const val MAX_SAMPLE = 4096
        private val SIGNATURE_DIGITS = Regex("""\d+(?:[.,]\d+)*""")
        private val NAME_ALIAS = Regex("""Player\d+""")
        private val SPACES = Regex("""\s+""")
    }
}
