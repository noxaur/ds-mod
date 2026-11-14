package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.DsModClient;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: the lightmap adds sky and block light to an ambient floor, so a white floor lights every block fully. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class FullbrightMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void dsmod$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (DsModClient.INSTANCE.getSettings().general.fullbright) state.ambientColor = LightmapRenderStateExtractor.WHITE;
    }
}
