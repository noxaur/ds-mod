package io.github.noxaur.dsmod.core.game

/**
 * A drop the server announced to the player who got it, such as `RARE DROP! (Summoning Eye) (✯ 90%)` or
 * `EPIC DROP! Golden Eye`. [tier] is the words before `DROP!`.
 */
data class Drop(val tier: String, val item: String) {
    companion object {
        // A player's chat line has their name first, so only the server's own lines start with the tier.
        private val announced = Regex("""^([A-Z]+(?: [A-Z]+)*) DROP! (?:\((.+?)\)(?: \(.*\))?|(.+))$""")

        /** The drop a chat line announces, or null for any other line. */
        fun parse(line: String): Drop? {
            if (" DROP! " !in line) return null
            val (tier, parenthesised, bare) = announced.find(line)?.destructured ?: return null
            return Drop(tier, parenthesised.ifEmpty { bare })
        }
    }
}
