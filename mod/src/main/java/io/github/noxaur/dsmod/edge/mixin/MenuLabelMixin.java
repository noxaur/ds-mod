package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.MenuSkin;
import io.github.noxaur.dsmod.edge.ui.Kit;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Themed Menus: a themed menu's title and inventory label are chrome, so they take the theme's ink. Vanilla's dark gray
 * stays the default, so a color the server gives the title still wins. The inventory screen draws its own labels.
 */
@Mixin({AbstractContainerScreen.class, InventoryScreen.class})
abstract class MenuLabelMixin {
    @ModifyArg(method = "extractLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"), index = 4)
    private int dsmod$ink(int color) {
        return MenuSkin.INSTANCE.skins((AbstractContainerScreen<?>) (Object) this) ? Kit.INSTANCE.getTheme().getInk() : color;
    }

    @ModifyArg(method = "extractLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"), index = 5)
    private boolean dsmod$shadow(boolean shadow) {
        return MenuSkin.INSTANCE.skins((AbstractContainerScreen<?>) (Object) this) ? Kit.INSTANCE.getTheme().getInkShadow() : shadow;
    }
}
