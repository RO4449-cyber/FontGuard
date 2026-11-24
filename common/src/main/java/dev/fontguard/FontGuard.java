package dev.fontguard;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FontGuard implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("FontGuard");

    /** Set by the mixins the first time they run, so we can prove protection is active. */
    public static volatile boolean fontHookActive = false;

    @Override
    public void onInitializeClient() {
        FontGuardConfig.load(FabricLoader.getInstance().getConfigDir().resolve("fontguard.properties"));
        LOGGER.info("[FontGuard] Active. server_pack_fonts={}, local_pack_fonts={}, disable_font_hinting={}",
                FontGuardConfig.serverPackFonts, FontGuardConfig.localPackFonts, FontGuardConfig.disableHinting);
    }
}
