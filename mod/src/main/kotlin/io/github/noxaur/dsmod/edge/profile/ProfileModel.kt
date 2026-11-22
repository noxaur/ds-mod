package io.github.noxaur.dsmod.edge.profile

import com.mojang.authlib.GameProfile
import io.github.noxaur.dsmod.core.profile.DrawnItem
import io.github.noxaur.dsmod.core.profile.Equipment
import io.github.noxaur.dsmod.edge.InventoryModel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.PlayerModelPart
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ResolvableProfile
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.PI

class ProfileModel(level: ClientLevel, val uuid: UUID, name: String) {
    @Volatile
    private var fetched: PlayerSkin? = null

    private val player = object : RemotePlayer(level, GameProfile(uuid, name)) {
        override fun getSkin(): PlayerSkin = fetched ?: DefaultPlayerSkin.get(uuid)

        override fun isModelPartShown(part: PlayerModelPart) = true
    }

    init {
        // @keep The server counts entity ids up from 1, so a negative one is never another entity's.
        player.id = MODEL_ID
        Minecraft.getInstance().playerSkinRenderCache().lookup(ResolvableProfile.createUnresolved(uuid))
            .thenAccept { info -> info.ifPresent { fetched = it.playerSkin() } }
    }

    fun dress(equipment: Equipment) {
        fun wear(slot: EquipmentSlot, item: DrawnItem?) = player.setItemSlot(slot, item?.toStack() ?: ItemStack.EMPTY)
        wear(EquipmentSlot.HEAD, equipment.helmet)
        wear(EquipmentSlot.CHEST, equipment.chestplate)
        wear(EquipmentSlot.LEGS, equipment.leggings)
        wear(EquipmentSlot.FEET, equipment.boots)
        wear(EquipmentSlot.MAINHAND, equipment.held)
    }

    fun draw(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, scale: Int, yaw: Float, pitch: Float) {
        val state = InventoryModel.state(player, yaw)
        val tip = Quaternionf().rotateX(pitch * (PI / 180).toFloat())
        val rotation = Quaternionf().rotateZ(PI.toFloat()).mul(tip)
        g.entity(state, scale.toFloat(), Vector3f(0f, state.boundingBoxHeight / 2f + InventoryModel.Y_OFFSET, 0f), rotation, tip, x0, y0, x1, y1)
    }

    private companion object {
        const val MODEL_ID = -1000
    }
}
