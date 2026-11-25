package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.Hooks;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@InputApproved(hash = "6264efb66a29")
@Mixin(ParticleEngine.class)
abstract class ExplosionParticleMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void dragen$hideExplosion(ParticleOptions options, double x, double y, double z, double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
        if ((ParticleTypes.EXPLOSION.equals(options.getType()) || ParticleTypes.EXPLOSION_EMITTER.equals(options.getType()))
            && Hooks.hidesExplosions()) {
            cir.setReturnValue(null);
        }
    }
}
