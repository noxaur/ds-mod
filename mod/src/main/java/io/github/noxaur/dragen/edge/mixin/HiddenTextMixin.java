package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.EditedText;
import io.github.noxaur.dragen.edge.FloatingTexts;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@InputApproved(hash = "d82209157795")
@Mixin(EntityRenderer.class)
abstract class HiddenTextMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void dragen$hidden(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EditedText edited && FloatingTexts.hidden(edited.dragen$edit())) cir.setReturnValue(false);
    }
}
