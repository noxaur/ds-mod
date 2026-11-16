package io.github.noxaur.dsmod.core.game

/**
 * A drop the server announced to the player who got it, such as `RARE DROP! (Summoning Eye) (✯ 90%)` or
 * `EPIC DROP! Golden Eye`. [tier] is the words before `DROP!`.
 */
data class Drop(val tier: String, val item: String) {
    companion object {
        /** The start of a drop line, for a feature's `line` handler to pass with [parse]. */
        val head = Regex("""[A-Z]+(?: [A-Z]+)* DROP! """)
        private val announced = Regex("""([A-Z]+(?: [A-Z]+)*) DROP! (?:\((.+?)\)(?: \(.*\))?|(.+))""")

        /** The drop a line that starts with [head] announces, or null when it does not parse. */
        fun parse(line: String): Drop? {
            val (tier, parenthesised, bare) = announced.matchEntire(line)?.destructured ?: return null
            return Drop(tier, parenthesised.ifEmpty { bare })
        }
    }
}
