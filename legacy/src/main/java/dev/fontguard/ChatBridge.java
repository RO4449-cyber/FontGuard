package dev.fontguard;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Chat API for Minecraft 1.20 - 1.21.11. */
final class ChatBridge {
    private ChatBridge() {}

    static void show(LocalPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
