package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.BossesNear
import io.github.noxaur.dsmod.core.game.NearBoss
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

/**
 * Finds the entities that may be bosses, as [BossesNear] says, and posts them to [game] when one comes or goes. Every
 * zealot is named, so an entity's name is read again only when the server gave it another one, which it does
 * on each hit. Entities are kept by their id unboxed, so a pass allocates only for a new entity or a new name.
 */
class BossPoller(private val game: Game) {
    private class Seen(var name: Component?, var boss: Boolean, var pass: Int)

    private val seen = Int2ObjectOpenHashMap<Seen>()
    private var bosses = emptyList<NearBoss>()
    private val found = ArrayList<Entity>()
    private var pass = 0
    private var tapped = game.tap

    fun poll(client: Minecraft) {
        if (!game.wants(BossesNear::class.java)) return
        val level = client.level ?: return
        pass++
        found.clear()
        for (entity in level.entitiesForRendering()) {
            // A mob that died stays a second for its fall, and a boss the server killed is gone at once, so a dying one is gone.
            if (entity !is LivingEntity || entity is Player || entity is ArmorStand || !entity.isAlive) continue
            val name = entity.customName
            var known = seen.get(entity.id)
            if (known == null) {
                known = Seen(name, isBoss(entity, name), pass)
                seen.put(entity.id, known)
            } else if (known.name !== name) {
                known.name = name
                known.boss = isBoss(entity, name)
            }
            known.pass = pass
            if (known.boss) found += entity
        }
        seen.values.removeIf { it.pass != pass }
        if (found.size == bosses.size && found.indices.all { found[it].id == bosses[it].id } && game.tap === tapped) return
        tapped = game.tap
        bosses = found.map { NearBoss(it.id, BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString(), (it.customName ?: Component.empty()).toText()) }
        game.post(System.currentTimeMillis(), BossesNear(bosses))
    }

    /** Drops what the last world held, since a new world numbers its entities again. */
    fun forget() {
        seen.clear()
        bosses = emptyList()
    }

    private fun isBoss(entity: Entity, name: Component?) = entity is EnderDragon || name != null && (entity is IronGolem || name.string.startsWith(SKULL))

    private companion object {
        const val SKULL = "☠"
    }
}
