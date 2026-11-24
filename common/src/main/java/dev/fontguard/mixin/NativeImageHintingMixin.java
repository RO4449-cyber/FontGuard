package dev.fontguard.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fontguard.FreeTypeHardening;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Same as {@link TrueTypeGlyphProviderHintingMixin}, for the glyph rasterising path in NativeImage. */
@Mixin(NativeImage.class)
public abstract class NativeImageHintingMixin {
    @Redirect(method = "*", require = 0, at = @At(value = "INVOKE", remap = false,
            target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
    private int fontguard$loadGlyph(FT_Face face, int glyphIndex, int flags) {
        return FreeType.FT_Load_Glyph(face, glyphIndex, FreeTypeHardening.flags(flags));
    }
}
