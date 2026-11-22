package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.Hooks;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@InputApproved(by = "noxaur", hash = "c5e6281a5531")
@Mixin(LightmapRenderStateExtractor.class)
abstract class FullbrightMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void dsmod$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (Hooks.fullbright()) state.ambientColor = LightmapRenderStateExtractor.WHITE;
    }
}
