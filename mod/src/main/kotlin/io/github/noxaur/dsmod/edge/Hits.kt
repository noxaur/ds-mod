package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.Hit
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.Refusals
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

object Hits {
    @InputApproved(reaches = ["Refusals.attackEntity"])
    fun register() {
        Refusals.attackEntity().register { player, level, _, entity, _ ->
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
