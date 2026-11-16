package io.github.noxaur.dsmod.edge;

import net.minecraft.network.chat.Component;

/** A text display that `FloatingTextMixin` gave the edit from `FloatingTexts`, so a renderer can read it. */
public interface EditedText {
    /** The text shown in place of the server's, `FloatingTexts.HIDDEN`, or null for the server's own. */
    Component dsmod$edit();
}
