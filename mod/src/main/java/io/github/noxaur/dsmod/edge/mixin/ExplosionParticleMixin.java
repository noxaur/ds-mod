package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.core.feature.general.ExplosionParticleHider;
import io.github.noxaur.dsmod.edge.DsModClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explosion Particle Hider: every particle is made here, so an explosion's cloud and its emitter end before they exist. */
@Mixin(ParticleEngine.class)
abstract class ExplosionParticleMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void dsmod$hideExplosion(ParticleOptions options, double x, double y, double z, double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
        if ((options.getType() == ParticleTypes.EXPLOSION || options.getType() == ParticleTypes.EXPLOSION_EMITTER)
            && DsModClient.INSTANCE.runs(ExplosionParticleHider.class)) {
            cir.setReturnValue(null);
        }
    }
}
