package dev.fontguard;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Chat API for Minecraft 26.1+. */
final class ChatBridge {
    private ChatBridge() {}

    static void show(LocalPlayer player, Component message) {
        player.sendSystemMessage(message);
    }
}
