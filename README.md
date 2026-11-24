# FontGuard

**A client-side Fabric mod that protects your computer from malicious fonts in Minecraft resource packs.**

In November 2026 a public proof of concept, [minecraft2shell](https://github.com/bikini/minecraft2shell), showed that a
Minecraft server can run native code on your computer just by sending you a resource pack. You join the server,
accept the pack, and a crafted font file takes over the game process. The demo opens Calculator, but the same
technique could install malware such as a RAT.

FontGuard stops this, and the wider family of font attacks it belongs to.

| | |
|---|---|
| **Minecraft** | 1.20 – 1.21.11 (one jar) and 26.1 – 26.3+ (one jar) |
| **Loader** | Fabric (and Quilt). No Fabric API needed. |
| **OS** | Windows, macOS, Linux |
| **Side** | Client only. Servers don't need it. |

## Download

Get the jar that matches your Minecraft version from [Releases](../../releases):

- `fontguard-fabric-1.20-1.21.11-<version>.jar` for **Minecraft 1.20 to 1.21.11**
- `fontguard-fabric-26.x-<version>.jar` for **Minecraft 26.1 and newer**

Put it in your `.minecraft/mods` folder. That's all. It's on by default with the safest settings.

## How the exploit works

1. **Delivery.** The server sets `resource-pack=` in `server.properties`. The pack contains 144 `.ttf` font files
   plus font JSON files that use the `ttf` provider.
2. **Trigger.** The server shows ordinary action-bar text (`F`, then `A`). To draw those letters, Minecraft asks
   **FreeType** (the native C font library bundled with the game through LWJGL) to load the glyphs from the
   malicious font.
3. **Memory corruption.** TrueType fonts contain small *hinting programs*, bytecode that FreeType runs in a
   virtual machine to adjust glyph outlines. The crafted font uses a nested composite glyph so the `SHZ`
   instruction writes past the end of a heap buffer (FreeType 2.14.3, `Ins_SHZ`). That write corrupts
   FreeType's own interpreter state.
4. **Taking control.** The font's `fpgm` table uses `IDEF` to redefine opcode `0x36` (SHZ). The corrupted state
   makes FreeType jump into that font-supplied code, which reads and writes interpreter memory, builds a ROP
   chain, and pivots the CPU stack into it.
5. **Payload.** The chain calls `GetProcAddress` → `WinExec("calc.exe")`. A real attacker would download and run
   malware instead.

The PoC targets Minecraft 26.3 on Windows x64. However, **the bug is in FreeType's bytecode interpreter, which
runs on every OS.** Only the final ROP gadgets are Windows-specific, and porting them is ordinary exploit work.
Every version since Minecraft switched to FreeType uses the same code path. 1.20.x uses `stb_truetype`, which
says itself that it is not safe for untrusted fonts.

## How FontGuard protects you

FontGuard has three independent layers. Any one of them alone stops minecraft2shell.

**1. Server fonts are blocked (default).**
Before Minecraft opens a `.ttf`, FontGuard checks which pack the file *actually* comes from. If it's a
server-sent or downloaded-world pack (`server/...`), the font is refused before native code ever sees a byte.
Minecraft skips that font and keeps using its normal one. The rest of the server's pack (textures, sounds,
models) still works. Pack names are assigned by your client, so a server can't disguise its pack.

**2. Every other font is scanned (default).**
Fonts from your own resource packs and from mods are checked by a pure-Java scanner first. Fonts are
refused if they:
- use `IDEF` to override a built-in TrueType instruction (the exploit's core trick)
- are malformed: tables or glyph data past the end of the file, broken `loca`, truncated bytecode,
  composite-glyph loops or excessive nesting, or more points/contours than the font's own `maxp` declares
  (a classic heap-overflow setup)
- are not plain TrueType (collections, CFF/OpenType, WOFF), which Minecraft doesn't support anyway and which
  would reach riskier FreeType parsers
- are unreasonably large (default 32 MB)

Tested against all 144 fonts in the minecraft2shell pack (all blocked) and 204 TrueType fonts shipped with macOS
(203 pass. The one refused is a bitmap-only font Minecraft can't load anyway).

**3. Font bytecode is never executed (default).**
FontGuard makes Minecraft load glyphs with `FT_LOAD_NO_HINTING | FT_LOAD_NO_AUTOHINT`, so FreeType's TrueType
bytecode interpreter, where this bug lives, never runs. This also protects against *future* interpreter bugs.
Minecraft's own fonts are bitmaps, so you won't see a difference. Custom TTF fonts may look very slightly softer.

When something is blocked, you'll see a gold chat message, and details go to `logs/latest.log`.

## Settings

`config/fontguard.properties` is created on first launch:

```properties
# BLOCK (default) = never load fonts from server/world packs.  SCAN = load only if the scan is clean.
server_pack_fonts=BLOCK
# Fonts from your own packs and mods: SCAN (default) | BLOCK | ALLOW
local_pack_fonts=SCAN
# Stop TrueType bytecode from ever running (recommended)
disable_font_hinting=true
notify_in_chat=true
max_font_size_mb=32
```

Server fonts can never be set to `ALLOW`.

## Limits (please read)

- FontGuard covers **fonts**, which is the attack surface minecraft2shell uses. Resource packs also contain PNG
  images and sounds, decoded by other native libraries. No public exploit exists for those today, but no mod can
  promise "100% safe". If you don't trust a server, **decline its resource pack.**
- It only protects Fabric/Quilt clients that have it installed. The real fix is for Mojang to update or sandbox
  FreeType. Keep your game updated.
- A NeoForge/Forge port is planned. Contributions welcome.

## Building from source

Requires a JDK (17+) and internet access. Gradle downloads the other JDKs it needs.

```bash
./gradlew build
```

Jars appear in `legacy/build/libs` and `modern/build/libs`. To run the scanner tests, including against the real
exploit pack if you have it:

```bash
./gradlew :core:test -Dfontguard.samplePack=/path/to/resource-pack.zip
```

The project has three parts:
- `common/` is the shared code: the scanner, the mixins, and the config.
- `legacy/` builds the 1.20–1.21.11 jar (Mojang names remapped to intermediary).
- `modern/` builds the 26.x jar (unobfuscated).

## License

FontGuard Non-Commercial License. See [LICENSE](LICENSE). The source is open: use it, fork it, and improve it.
**You may not make money from it.** That means no selling, no paywalls, and no monetized re-uploads.
