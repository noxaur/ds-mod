package io.github.noxaur.dragen.api

import kotlin.math.sqrt

public data class Vec(public val x: Double, public val y: Double, public val z: Double) {
    public operator fun plus(other: Vec): Vec = Vec(x + other.x, y + other.y, z + other.z)

    public operator fun minus(other: Vec): Vec = Vec(x - other.x, y - other.y, z - other.z)

    public operator fun times(scale: Double): Vec = Vec(x * scale, y * scale, z * scale)

    public val length: Double get() = sqrt(x * x + y * y + z * z)

    public companion object {
        public val ZERO: Vec = Vec(0.0, 0.0, 0.0)
    }
}

public data class Box(public val center: Vec, public val width: Double, public val height: Double)
