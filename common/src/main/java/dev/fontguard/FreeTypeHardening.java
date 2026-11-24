package dev.fontguard;

/** FreeType load-flag hardening. */
public final class FreeTypeHardening {
    /** FT_LOAD_NO_HINTING: do not run the TrueType bytecode interpreter (fpgm/prep/glyph programs). */
    private static final int FT_LOAD_NO_HINTING = 0x2;
    /** FT_LOAD_NO_AUTOHINT: also skip FreeType's auto-hinter. */
    private static final int FT_LOAD_NO_AUTOHINT = 0x8000;
    private static volatile boolean logged = false;

    private FreeTypeHardening() {}

    public static int flags(int original) {
        if (!FontGuardConfig.disableHinting) {
            return original;
        }
        if (!logged) {
            logged = true;
            FontGuard.LOGGER.info("[FontGuard] Font hinting disabled: TrueType font bytecode will not be executed");
        }
        return original | FT_LOAD_NO_HINTING | FT_LOAD_NO_AUTOHINT;
    }
}
