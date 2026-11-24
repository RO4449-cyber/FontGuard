package dev.fontguard.mixin;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import dev.fontguard.FreeTypeHardening;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Loads every glyph with FT_LOAD_NO_HINTING so the TrueType bytecode interpreter never runs. */
@Mixin(TrueTypeGlyphProvider.class)
public abstract class TrueTypeGlyphProviderHintingMixin {
    @Redirect(method = "*", require = 0, at = @At(value = "INVOKE", remap = false,
            target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
    private int fontguard$loadGlyph(FT_Face face, int glyphIndex, int flags) {
        return FreeType.FT_Load_Glyph(face, glyphIndex, FreeTypeHardening.flags(flags));
    }
}
