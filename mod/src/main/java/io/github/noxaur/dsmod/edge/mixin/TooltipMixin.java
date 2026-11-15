package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.MenuSkin;
import io.github.noxaur.dsmod.edge.ui.Kit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Themed Menus: a tooltip with no style of its own gets the kit's backing. dragsim.co gives its items a style per rarity
 * from its resource pack, such as `dragsim:legendary`, and those keep the server's look.
 */
@Mixin(TooltipRenderUtil.class)
abstract class TooltipMixin {
    @Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void dsmod$theme(GuiGraphicsExtractor graphics, int x, int y, int w, int h, @Nullable Identifier style, CallbackInfo ci) {
        if (style != null || !MenuSkin.INSTANCE.getThemed()) return;
        Kit.INSTANCE.tooltip(graphics, x, y, w, h);
        ci.cancel();
    }
}
