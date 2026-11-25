package io.github.noxaur.dragen.edge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.noxaur.dragen.edge.HeldItems;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** @keep The client sends the swing on every click before it looks at this, so a slow or fast swing changes nothing the server sees. */
@InputApproved(by = "noxaur", hash = "b2ff2c2bc041")
@Mixin(LivingEntity.class)
abstract class SwingSpeedMixin {
    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int dragen$swingTicks(int ticks) {
        return HeldItems.INSTANCE.swingTicks((LivingEntity) (Object) this, ticks);
    }
}
