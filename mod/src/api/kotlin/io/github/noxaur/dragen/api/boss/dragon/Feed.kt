package io.github.noxaur.dragen.api.boss.dragon

import io.github.noxaur.dragen.api.ServerClock
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.world.BlockArea

public data class Watch(public val blocks: Set<String>, public val areas: List<BlockArea>, public val entities: Set<String>) {
    public companion object {
        public val NOTHING: Watch = Watch(emptySet(), emptyList(), emptySet())
    }
}

public data class SeenBossBar(public val name: String, public val progress: Double)

public data class SeenEntity(
    public val id: Int,
    public val type: String,
    public val position: Vec,
    public val yaw: Double,
    public val pitch: Double,
    public val name: String,
    public val parts: List<Part>,
)

public interface DragonFeed {
    public val watch: Watch

    public fun block(x: Int, y: Int, z: Int, state: String)

    public fun chat(text: String)

    public fun tabList(rows: List<String>)

    public fun sidebar(lines: List<String>)

    public fun bossBars(bars: List<SeenBossBar>)

    public fun entities(seen: List<SeenEntity>)

    public fun tick(gameTime: Long)

    public fun forget(area: BlockArea)

    public fun worldChanged()
}

public class DragonSystem(clock: ServerClock) {
    private val dragons = Dragons(clock)

    public val api: DragonApi get() = dragons

    public val feed: DragonFeed get() = dragons
}
