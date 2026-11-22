package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.EditedText;
import io.github.noxaur.dsmod.edge.FloatingTexts;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@InputApproved(hash = "d7de7ed952c5")
@Mixin(EntityRenderer.class)
abstract class HiddenTextMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void dsmod$hidden(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EditedText edited && FloatingTexts.hidden(edited.dsmod$edit())) cir.setReturnValue(false);
    }
}
