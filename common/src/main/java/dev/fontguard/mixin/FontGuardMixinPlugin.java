package dev.fontguard.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Minecraft 1.20-1.20.x render fonts with STB instead of FreeType. The hinting mixins only make
 * sense (and only link) when LWJGL FreeType is present, so they are skipped otherwise.
 */
public final class FontGuardMixinPlugin implements IMixinConfigPlugin {
    private static final boolean HAS_FREETYPE =
            FontGuardMixinPlugin.class.getClassLoader().getResource("org/lwjgl/util/freetype/FreeType.class") != null;

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("HintingMixin")) {
            return HAS_FREETYPE;
        }
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode c, String m, IMixinInfo i) {}
    @Override public void postApply(String t, ClassNode c, String m, IMixinInfo i) {}
}
