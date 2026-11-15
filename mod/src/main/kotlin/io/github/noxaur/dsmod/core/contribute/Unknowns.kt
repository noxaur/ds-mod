package io.github.noxaur.dsmod.core.contribute

import io.github.noxaur.dsmod.core.backend.Sighting
import io.github.noxaur.dsmod.core.capture.PlayerNames
import io.github.noxaur.dsmod.core.capture.Redactor

/**
 * Collects what reached a feature and failed to parse, for the player who agreed to send it. A sighting is one
 * [Sighting.kind] and one [Sighting.signature], which is the line with its digits and names replaced, so two lines
 * of the same shape are one sighting with one redacted example. [own] is the player's own name.
 * Call it on the client thread.
 */
class Unknowns(private val own: () -> Set<String>) {
    private val pending = LinkedHashMap<Pair<String, String>, Sighting>()
    private val sending = HashSet<Pair<String, String>>()
    private val sent = HashSet<Pair<String, String>>()

    /** The lines already read, as they came. A menu resends the same line for every slot, so most calls end here. */
    private val seen = HashSet<Pair<String, String>>()
    private var names = emptySet<String>()
    private var redactor = Redactor(names)

    /** Records [line] as something [kind] could not read, unless this shape is already waiting or went out. */
    fun see(kind: String, line: String) {
        if (pending.size >= MAX_BATCH) return
        if (seen.size >= MAX_SEEN) seen.clear()
        if (!seen.add(kind to line)) return
        // A redactor compiles its names into a pattern, and most lines name nobody but the player.
        val named = PlayerNames.inText(line) + own()
        if (named != names) {
            names = named
            redactor = Redactor(named)
        }
        val redacted = redactor.names(line)
        val sample = redacted.take(MAX_SAMPLE)
        val signature = SPACES.replace(SIGNATURE_DIGITS.replace(NAME_ALIAS.replace(redacted, "<player>"), "#"), " ")
            .trim().take(MAX_SIGNATURE)
        val key = kind.take(MAX_KIND) to signature
        if (key in sent || key in sending || key in pending) return
        pending[key] = Sighting(key.first, signature, sample)
    }

    val waiting get() = pending.isNotEmpty()

    private var waitingSince = -1L
    private var lastReport: Long? = null

    /**
     * Whether a report should go at [now], and if so counts it as sent. The first goes [FIRST_MILLIS] after something
     * began to wait, so a short session still reports, and each later one [SPACING_MILLIS] after the one before.
     */
    fun due(now: Long): Boolean {
        if (!waiting) {
            waitingSince = -1
            return false
        }
        if (waitingSince < 0) waitingSince = now
        val last = lastReport
        if (if (last == null) now - waitingSince < FIRST_MILLIS else now - last < SPACING_MILLIS) return false
        lastReport = now
        waitingSince = -1
        return true
    }

    /** Takes what is waiting, to send. Tell [finished] how it went, and until then these shapes are not collected again. */
    fun take(): List<Sighting> {
        val batch = pending.values.toList()
        sending += pending.keys
        pending.clear()
        return batch
    }

    /**
     * Ends what [take] began for [batch]. When it was [delivered], its shapes are not collected again this session.
     * Otherwise they wait for the next report, as far as a batch has room, and the rest may be seen again.
     */
    fun finished(batch: List<Sighting>, delivered: Boolean) {
        for (sighting in batch) {
            val key = sighting.kind to sighting.signature
            if (!sending.remove(key)) continue
            if (delivered) sent += key else if (pending.size < MAX_BATCH) pending[key] = sighting
        }
        // A shape that found no room must be read again to come back.
        if (!delivered) seen.clear()
    }

    /** Forgets what is waiting, for a player who switched the report off. */
    fun drop() {
        waitingSince = -1
        pending.clear()
        seen.clear()
    }

    companion object {
        const val FIRST_MILLIS = 2 * 60 * 1000L
        const val SPACING_MILLIS = 20 * 60 * 1000L
        private const val MAX_BATCH = 50
        private const val MAX_SEEN = 1024
        private const val MAX_KIND = 64
        private const val MAX_SIGNATURE = 256
        private const val MAX_SAMPLE = 4096
        private val SIGNATURE_DIGITS = Regex("""\d+(?:[.,]\d+)*""")
        private val NAME_ALIAS = Regex("""Player\d+""")
        private val SPACES = Regex("""\s+""")
    }
}
