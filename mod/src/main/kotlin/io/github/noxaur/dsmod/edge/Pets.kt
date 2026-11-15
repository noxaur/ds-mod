package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.PetNear
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Finds the pet nearest the player and posts it to [game] when it changes. A pet is an armor stand wearing a player
 * head and named `[Lvl N] Name`, and only stands within [RANGE] blocks are looked at, so a poll reads a handful of
 * entities.
 */
class PetPoller(private val game: Game) {
    private var name = ""
    private var stack: ItemStack? = null
    private var tapped = game.tap

    fun poll(client: Minecraft) {
        if (!game.wants(PetNear::class.java)) return
        val player = client.player ?: return
        val level = client.level ?: return
        var nearest: ArmorStand? = null
        var distance = Double.MAX_VALUE
        for (stand in level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(RANGE))) {
            val named = stand.customName?.string ?: continue
            if (!named.startsWith(LEVEL) || !stand.getItemBySlot(EquipmentSlot.HEAD).`is`(Items.PLAYER_HEAD)) continue
            val away = stand.distanceToSqr(player)
            if (away < distance) {
                distance = away
                nearest = stand
            }
        }
        val pet = nearest ?: return
        val head = pet.getItemBySlot(EquipmentSlot.HEAD)
        val named = pet.customName!!
        val previous = stack
        if (named.string == name && previous != null && ItemStack.isSameItemSameComponents(previous, head) && game.tap === tapped) return
        name = named.string
        stack = head
        tapped = game.tap
        game.post(System.currentTimeMillis(), PetNear(named.toText(), head.toItem()))
    }

    private companion object {
        const val RANGE = 6.0
        const val LEVEL = "[Lvl "
    }
}
