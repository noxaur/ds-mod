package io.github.noxaur.dsmod.edge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.noxaur.dsmod.edge.QuieterAfks;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** @keep Every sound's volume, the music's included, passes through here when it starts and when a volume changes. */
@InputApproved(by = "noxaur", hash = "580cf8bb1fb9")
@Mixin(SoundEngine.class)
abstract class QuietSoundMixin {
    @ModifyReturnValue(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"))
    private float dsmod$quiet(float volume) {
        return QuieterAfks.volume(volume);
    }
}
