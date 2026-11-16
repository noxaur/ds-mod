package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.Hit
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

/**
 * Posts [Hit] for the player's own attack on a living entity, which the server says nothing of. It only watches: every
 * attack passes on as the player made it. Kept apart from the rest of the edge because reading a Fabric player event
 * is an input site the user audits.
 */
object Hits {
    fun register() {
        AttackEntityCallback.EVENT.register { player, level, _, entity, _ ->
            // Fabric runs this on both sides in singleplayer, and only the client's own player's attack is the player's hit.
            val game = DsModClient.game
            if (level.isClientSide && player === Minecraft.getInstance().player && entity is LivingEntity && entity !is ArmorStand &&
                game.wants(Hit::class.java)
            ) {
                game.post(System.currentTimeMillis(), Hit(BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()))
            }
            InteractionResult.PASS
        }
    }
}
