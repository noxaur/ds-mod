package io.github.noxaur.dsmod.edge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.noxaur.dsmod.edge.Cameras;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera and Free Look: the third-person camera's angle, where it starts from and how far it backs out. The
 * angle and the start are set after the game has put the camera at the player's eyes and before it backs the
 * camera out along that angle, so a tilt also moves the camera and the wall check runs from the lifted start.
 */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private Level level;
    @Shadow private Entity entity;
    @Shadow private Vec3 position;
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "update", at = @At("HEAD"))
    private void dsmod$frame(DeltaTracker deltaTracker, CallbackInfo ci) {
        Cameras.INSTANCE.frame(minecraft);
    }

    @Inject(
        method = "alignWithEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;", ordinal = 0)
    )
    private void dsmod$place(float partialTicks, CallbackInfo ci) {
        var type = minecraft.options.getCameraType();
        if (type.isFirstPerson()) {
            Cameras.INSTANCE.firstPerson();
            return;
        }
        setRotation(Cameras.INSTANCE.yaw(yRot), Cameras.INSTANCE.pitch(xRot, type.isMirrored()));
        setPosition(Cameras.INSTANCE.lift(level, entity, position));
    }

    @ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float dsmod$distance(float vanilla) {
        return Cameras.INSTANCE.distance(vanilla);
    }

    @ModifyReturnValue(method = "getMaxZoom", at = @At("RETURN"))
    private float dsmod$slide(float clear) {
        return Cameras.INSTANCE.slide(clear);
    }
}
