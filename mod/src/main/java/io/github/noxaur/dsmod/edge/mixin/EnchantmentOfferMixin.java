package io.github.noxaur.dsmod.edge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.noxaur.dsmod.edge.MenuSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EnchantmentScreen.class)
abstract class EnchantmentOfferMixin {
    @ModifyArg(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIIIZ)V"), index = 5)
    private int dsmod$nameInk(int color) {
        return MenuSkin.INSTANCE.offerInk((AbstractContainerScreen<?>) (Object) this, color);
    }

    @ModifyArg(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIIIZ)V"), index = 6)
    private boolean dsmod$nameShadow(boolean shadow) {
        return MenuSkin.INSTANCE.offerShadow((AbstractContainerScreen<?>) (Object) this, shadow);
    }

    @WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void dsmod$cost(GuiGraphicsExtractor graphics, Font font, String cost, int x, int y, int color, Operation<Void> original) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (MenuSkin.INSTANCE.skins(screen)) graphics.text(font, cost, x, y, MenuSkin.INSTANCE.offerInk(screen, color), MenuSkin.INSTANCE.offerShadow(screen, true));
        else original.call(graphics, font, cost, x, y, color);
    }
}
