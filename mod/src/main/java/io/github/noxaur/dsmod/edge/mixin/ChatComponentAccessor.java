package io.github.noxaur.dsmod.edge.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chat's wrapped lines, each with the message it is part of. Minecraft keeps them private. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor
    List<GuiMessage.Line> getTrimmedMessages();
}
