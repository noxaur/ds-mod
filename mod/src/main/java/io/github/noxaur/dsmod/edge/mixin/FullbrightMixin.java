package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.Hooks;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class FullbrightMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void dsmod$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (Hooks.fullbright()) state.ambientColor = LightmapRenderStateExtractor.WHITE;
    }
}
