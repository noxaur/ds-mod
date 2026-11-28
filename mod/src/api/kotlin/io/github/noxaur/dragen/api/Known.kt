package io.github.noxaur.dragen.api

public sealed interface Known<out T> {
    public data class Value<out T>(public val value: T) : Known<T>

    public data object Unknown : Known<Nothing>
}

@JvmInline
public value class Progress(public val fraction: Double) {
    init {
        require(fraction in 0.0..1.0) { "Progress is in [0, 1], not $fraction" }
    }
}
