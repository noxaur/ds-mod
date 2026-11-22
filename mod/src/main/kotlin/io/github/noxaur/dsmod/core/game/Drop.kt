package io.github.noxaur.dsmod.core.game

data class Drop(val tier: String, val item: String) {
    companion object {
        val head = Regex("""[A-Z]+(?: [A-Z]+)* DROP! """)
        private val announced = Regex("""([A-Z]+(?: [A-Z]+)*) DROP! (?:\((.+?)\)(?: \(.*\))?|(.+))""")

        fun parse(line: String): Drop? {
            val (tier, parenthesised, bare) = announced.matchEntire(line)?.destructured ?: return null
            return Drop(tier, parenthesised.ifEmpty { bare })
        }
    }
}
