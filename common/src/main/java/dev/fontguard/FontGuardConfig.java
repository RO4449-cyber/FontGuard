package dev.fontguard;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Settings stored in {@code config/fontguard.properties}. Safe defaults are used if the file is missing or broken. */
public final class FontGuardConfig {
    public enum Policy { BLOCK, SCAN, ALLOW }

    /** Fonts that come from a server or world resource pack. ALLOW is not accepted here. */
    public static volatile Policy serverPackFonts = Policy.BLOCK;
    /** Fonts from your own resource packs and from mods. */
    public static volatile Policy localPackFonts = Policy.SCAN;
    /** Turns off TrueType hinting so font bytecode is never executed. */
    public static volatile boolean disableHinting = true;
    public static volatile boolean notifyInChat = true;
    public static volatile long maxFontBytes = 32L * 1024 * 1024;

    private static final String HEADER = String.join("\n",
            "FontGuard settings - protects against malicious fonts in resource packs (e.g. minecraft2shell).",
            "",
            "server_pack_fonts: fonts sent by a server (or a downloaded world)",
            "    BLOCK (default, safest) = never load them",
            "    SCAN                    = load only if the security scan finds nothing dangerous",
            "local_pack_fonts: fonts from your own resource packs and mods",
            "    SCAN (default) | BLOCK | ALLOW",
            "disable_font_hinting: true (default) stops font bytecode from ever running",
            "notify_in_chat: show a chat message when a font is blocked",
            "max_font_size_mb: refuse fonts bigger than this");

    private FontGuardConfig() {}

    public static void load(Path file) {
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                FontGuard.LOGGER.warn("[FontGuard] Could not read {}, using safe defaults", file, e);
            }
        }
        serverPackFonts = policy(p, "server_pack_fonts", Policy.BLOCK);
        if (serverPackFonts == Policy.ALLOW) {
            FontGuard.LOGGER.warn("[FontGuard] server_pack_fonts=ALLOW is not supported; using SCAN");
            serverPackFonts = Policy.SCAN;
        }
        localPackFonts = policy(p, "local_pack_fonts", Policy.SCAN);
        disableHinting = bool(p, "disable_font_hinting", true);
        notifyInChat = bool(p, "notify_in_chat", true);
        long mb = 32;
        try {
            mb = Long.parseLong(p.getProperty("max_font_size_mb", "32").trim());
        } catch (NumberFormatException ignored) {
        }
        maxFontBytes = Math.max(1, Math.min(mb, 256)) * 1024 * 1024;
        save(file);
    }

    private static void save(Path file) {
        Properties p = new Properties();
        p.setProperty("server_pack_fonts", serverPackFonts.name());
        p.setProperty("local_pack_fonts", localPackFonts.name());
        p.setProperty("disable_font_hinting", Boolean.toString(disableHinting));
        p.setProperty("notify_in_chat", Boolean.toString(notifyInChat));
        p.setProperty("max_font_size_mb", Long.toString(maxFontBytes / 1024 / 1024));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(new java.io.OutputStreamWriter(out, StandardCharsets.ISO_8859_1), HEADER);
            }
        } catch (IOException e) {
            FontGuard.LOGGER.warn("[FontGuard] Could not write {}", file, e);
        }
    }

    private static Policy policy(Properties p, String key, Policy def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            return Policy.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            FontGuard.LOGGER.warn("[FontGuard] Unknown value '{}' for {}, using {}", v, key, def);
            return def;
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }
}
