package io.github.noxaur.dsmod.core.game

private const val BEDROCK = "minecraft:bedrock"
private val AIR = setOf("minecraft:air", "minecraft:cave_air", "minecraft:void_air")

private const val RECENT_TICKS = 20
private const val NEAR_SQUARED = 16

/** @keep The server says only that a block became bedrock, so who broke it is read from what the player did. A capture showed
 * the player's own break arriving up to 19 ticks after they dug it, Pickobulus breaking all it breaks in one tick within 3
 * blocks of the block they right-clicked, and spread breaking its blocks within 4 of the one it spread from, in its tick. */
class BreakSorter {
    private class Spot(val x: Int, val y: Int, val z: Int, var tick: Long)

    private val dug = HashMap<Long, Spot>()
    private val used = HashMap<Long, Spot>()
    private val broken = ArrayList<BlockBroken>()
    private var tick = 0L

    fun dig(x: Int, y: Int, z: Int) = mark(dug, x, y, z)

    fun use(x: Int, y: Int, z: Int) = mark(used, x, y, z)

    fun changed(x: Int, y: Int, z: Int, was: String, now: String) {
        if (now == BEDROCK && was != BEDROCK && was !in AIR) broken += BlockBroken(was, x, y, z, tick, Breaker.OTHER)
    }

    fun tick(): List<BlockBroken> {
        val sorted = if (broken.isEmpty()) emptyList() else sort()
        broken.clear()
        tick++
        forget(dug)
        forget(used)
        return sorted
    }

    private fun sort(): List<BlockBroken> {
        val own = broken.filter { key(it.x, it.y, it.z) in dug }
        return broken.map { block ->
            when {
                key(block.x, block.y, block.z) in dug -> block.copy(by = Breaker.PLAYER)
                own.any { near(block, it.x, it.y, it.z) } || used.values.any { near(block, it.x, it.y, it.z) } -> block.copy(by = Breaker.WITH_PLAYER)
                else -> block
            }
        }
    }

    private fun mark(spots: HashMap<Long, Spot>, x: Int, y: Int, z: Int) {
        spots.getOrPut(key(x, y, z)) { Spot(x, y, z, tick) }.tick = tick
    }

    private fun forget(spots: HashMap<Long, Spot>) {
        if (spots.isNotEmpty()) spots.values.removeIf { tick - it.tick > RECENT_TICKS }
    }

    private fun near(block: BlockBroken, x: Int, y: Int, z: Int): Boolean {
        val dx = block.x - x
        val dy = block.y - y
        val dz = block.z - z
        return dx * dx + dy * dy + dz * dz <= NEAR_SQUARED
    }

    private fun key(x: Int, y: Int, z: Int) = ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)
}
