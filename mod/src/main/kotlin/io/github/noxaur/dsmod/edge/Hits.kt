package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.Hit
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

object Hits {
    fun register() {
        AttackEntityCallback.EVENT.register { player, level, _, entity, _ ->
            // @keep Fabric runs this on both sides in singleplayer, and only the client's own player's attack is the player's hit.
            val game = DsModClient.game
            if (level.isClientSide && player === Minecraft.getInstance().player && entity is LivingEntity && entity !is ArmorStand &&
                game.wants(Hit::class.java)
            ) {
                game.post(System.currentTimeMillis(), Hit(entity.typeId))
            }
            InteractionResult.PASS
        }
    }
}
