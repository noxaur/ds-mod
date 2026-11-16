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

/**
 * The player looked up, drawn by the game's own player renderer in a box on the screen and turned by the screen's
 * turntable. The player is not in the world, so the game has no skin for them: the skin is fetched from Mojang by
 * their id, as the game fetches a head's, and the default skin for that id shows until it arrives.
 */
class ProfileModel(level: ClientLevel, val uuid: UUID, name: String) {
    @Volatile
    private var fetched: PlayerSkin? = null

    private val player = object : RemotePlayer(level, GameProfile(uuid, name)) {
        override fun getSkin(): PlayerSkin = fetched ?: DefaultPlayerSkin.get(uuid)

        // A player out of the world has no settings for their skin's outer layers, and a viewer wants to see all of it.
        override fun isModelPartShown(part: PlayerModelPart) = true
    }

    init {
        // The renderer reads the id, and the world never gives one to an entity it does not hold. The server counts
        // ids up from 1, so a negative one is never another entity's.
        player.id = MODEL_ID
        Minecraft.getInstance().playerSkinRenderCache().lookup(ResolvableProfile.createUnresolved(uuid))
            .thenAccept { info -> info.ifPresent { fetched = it.playerSkin() } }
    }

    /** Puts on what the profile says they wear and hold, or nothing where it says nothing. */
    fun dress(equipment: Equipment) {
        fun wear(slot: EquipmentSlot, item: DrawnItem?) = player.setItemSlot(slot, item?.toStack() ?: ItemStack.EMPTY)
        wear(EquipmentSlot.HEAD, equipment.helmet)
        wear(EquipmentSlot.CHEST, equipment.chestplate)
        wear(EquipmentSlot.LEGS, equipment.leggings)
        wear(EquipmentSlot.FEET, equipment.boots)
        wear(EquipmentSlot.MAINHAND, equipment.held)
    }

    /**
     * Draws the player in the box from [x0], [y0] to [x1], [y1], [scale] pixels to a block, turned [yaw] degrees from
     * facing the viewer and tipped [pitch] degrees. It is how the inventory draws the player, with the turn set here
     * in place of following the mouse.
     */
    fun draw(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, scale: Int, yaw: Float, pitch: Float) {
        // The screen writes the name above the box.
        val state = InventoryModel.state(player, yaw)
        val tip = Quaternionf().rotateX(pitch * (PI / 180).toFloat())
        val rotation = Quaternionf().rotateZ(PI.toFloat()).mul(tip)
        g.entity(state, scale.toFloat(), Vector3f(0f, state.boundingBoxHeight / 2f + InventoryModel.Y_OFFSET, 0f), rotation, tip, x0, y0, x1, y1)
    }

    private companion object {
        const val MODEL_ID = -1000
    }
}
