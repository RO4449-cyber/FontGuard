package dev.fontguard.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import dev.fontguard.FontGuard;
import dev.fontguard.FontGuardConfig;
import dev.fontguard.FontGuardConfig.Policy;
import dev.fontguard.Notifier;
import dev.fontguard.PackTrust;
import dev.fontguard.scan.ScanResult;
import dev.fontguard.scan.TtfScanner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The single place where Minecraft turns a resource-pack ".ttf" file into a native FreeType (or STB) font.
 * Every TrueType font goes through here before any native code sees its bytes.
 *
 * 1. HEAD: find which pack the font file really comes from. Server/world packs are blocked by default.
 * 2. readResource: the exact bytes that would be handed to native code are scanned; dangerous fonts
 *    are freed and rejected. Minecraft catches the IOException, logs it, and simply skips that font.
 */
@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class TrueTypeGlyphProviderDefinitionMixin {
    @Unique
    private static final String FONTGUARD_ALLOW = "\u0000allow";
    @Unique
    private static final ThreadLocal<String> fontguard$context = new ThreadLocal<>();

    @Inject(method = "load", at = @At("HEAD"))
    private void fontguard$checkSource(ResourceManager resourceManager, CallbackInfoReturnable<GlyphProvider> cir)
            throws IOException {
        FontGuard.fontHookActive = true;
        fontguard$context.remove();
        var file = ((TrueTypeGlyphProviderDefinition) (Object) this).location().withPrefix("font/");
        var resource = resourceManager.getResource(file);
        if (resource.isEmpty()) {
            return; // Minecraft will fail with "file not found" by itself
        }
        String packId = resource.get().sourcePackId();
        boolean server = PackTrust.isServerPack(packId);
        Policy policy = server ? FontGuardConfig.serverPackFonts : FontGuardConfig.localPackFonts;
        String source = server ? "the server's resource pack" : "resource pack '" + packId + "'";

        if (policy == Policy.BLOCK) {
            FontGuard.LOGGER.warn("[FontGuard] BLOCKED font {} from {} (policy: block)", file, source);
            Notifier.blocked(source, "fonts from this source are disabled in fontguard.properties");
            throw new IOException("[FontGuard] Blocked font " + file + " from " + source);
        }
        fontguard$context.set(policy == Policy.ALLOW ? FONTGUARD_ALLOW : source + "\n" + file);
    }

    @Redirect(method = "load", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/TextureUtil;readResource(Ljava/io/InputStream;)Ljava/nio/ByteBuffer;"))
    private ByteBuffer fontguard$scanBytes(InputStream in) throws IOException {
        ByteBuffer buf = TextureUtil.readResource(in);
        String ctx = fontguard$context.get();
        fontguard$context.remove();
        if (FONTGUARD_ALLOW.equals(ctx)) {
            return buf;
        }
        String source = "a resource pack", file = "font";
        if (ctx != null) {
            int nl = ctx.indexOf('\n');
            source = ctx.substring(0, nl);
            file = ctx.substring(nl + 1);
        }
        // Older versions return the buffer un-flipped (data in [0, position)), newer ones flipped ([0, limit)).
        int length = buf.position() > 0 ? buf.position() : buf.limit();
        ByteBuffer view = buf.duplicate();
        view.position(0);
        view.limit(length);
        ScanResult result = TtfScanner.scan(view, FontGuardConfig.maxFontBytes);
        if (!result.warnings().isEmpty()) {
            FontGuard.LOGGER.info("[FontGuard] Font {} from {} warnings: {}", file, source, result.warnings());
        }
        if (result.isDangerous()) {
            MemoryUtil.memFree(buf);
            FontGuard.LOGGER.warn("[FontGuard] BLOCKED dangerous font {} from {}: {}", file, source, result.dangers());
            Notifier.blocked(source, result.summary());
            throw new IOException("[FontGuard] Blocked dangerous font " + file + ": " + result.summary());
        }
        return buf;
    }
}
