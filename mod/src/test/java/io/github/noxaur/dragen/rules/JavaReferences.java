package io.github.noxaur.dragen.rules;

import io.github.noxaur.dragen.core.feature.Feature;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class JavaLiteralReference {
    Function<String, MutableComponent> run() {
        return Component::literal;
    }
}

final class JavaFillReference {
    Runnable run(GuiGraphicsExtractor g) {
        return () -> g.fill(0, 0, 1, 1, 0);
    }
}

final class JavaEnabledReference {
    BooleanSupplier run(Feature feature) {
        return feature::getEnabled;
    }
}

final class JavaHooksScreen {
    Object run() {
        return net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT;
    }
}

final class JavaLooksUp {
    Object run() {
        return io.github.noxaur.dragen.edge.DragenClient.INSTANCE.getGame();
    }
}

final class JavaRefersSend {
    java.util.function.Consumer<String> run(net.minecraft.client.multiplayer.ClientPacketListener connection) {
        return connection::sendChat;
    }
}
