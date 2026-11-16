package io.github.noxaur.dsmod.edge.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chat screen's command suggestions, which Minecraft keeps private. */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
    @Accessor
    CommandSuggestions getCommandSuggestions();
}
