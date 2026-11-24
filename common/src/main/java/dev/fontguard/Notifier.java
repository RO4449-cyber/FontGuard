package dev.fontguard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Collects "font blocked" events (from loader threads) and reports them in chat on the main thread. */
public final class Notifier {
    private static final Map<String, AtomicInteger> PENDING = new ConcurrentHashMap<>();
    private static volatile String lastReason = "";

    private Notifier() {}

    public static void blocked(String source, String reason) {
        PENDING.computeIfAbsent(source, k -> new AtomicInteger()).incrementAndGet();
        lastReason = reason;
    }

    /** Called every client tick. */
    public static void flush(Minecraft mc) {
        if (PENDING.isEmpty() || mc.player == null) return;
        boolean show = FontGuardConfig.notifyInChat;
        for (String source : PENDING.keySet()) {
            AtomicInteger n = PENDING.remove(source);
            if (n == null || !show) continue;
            String msg = "[FontGuard] Blocked " + n.get() + " font file(s) from " + source + ". "
                    + (source.startsWith("the server") ? "Server fonts are blocked to protect your computer."
                            : "Reason: " + lastReason);
            ChatBridge.show(mc.player, Component.literal(msg).withStyle(ChatFormatting.GOLD));
        }
    }
}
