package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.Slots;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rarity marks on the hotbar: vanilla draws its frame, then each item here, so a mark's back goes between them. */
@Mixin(Hud.class)
abstract class HudHotbarMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void dsmod$markBehind(GuiGraphicsExtractor graphics, int x, int y, DeltaTracker delta, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        Slots.hotbarSlot(graphics, x, y);
    }
}
