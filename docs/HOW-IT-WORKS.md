# How the exploit works, and how FontGuard stops it

## The exploit: minecraft2shell

[minecraft2shell](https://github.com/bikini/minecraft2shell) is a public proof of concept from November 2026. It
turns a resource pack into **remote code execution (RCE)**: code chosen by the server runs on the player's computer.

1. **Delivery.** The server sets `resource-pack=` in `server.properties`. The pack contains 144 `.ttf` font
   files, plus font JSON files that use the `ttf` provider.
2. **Trigger.** The server shows ordinary action-bar text (`F`, then `A`). To draw those letters, Minecraft asks
   **FreeType** to load the glyphs from the malicious font. FreeType is the native C font library bundled with the
   game through LWJGL.
3. **Memory corruption.** TrueType fonts contain small *hinting programs*: bytecode that FreeType runs in a virtual
   machine to adjust glyph outlines. The crafted font uses a nested composite glyph so the `SHZ` instruction writes
   past the end of a heap buffer (FreeType 2.14.3, `Ins_SHZ`). That write corrupts FreeType's own interpreter
   state.
4. **Taking control.** The font's `fpgm` table uses `IDEF` to redefine opcode `0x36` (SHZ). The corrupted state
   makes FreeType jump into that font-supplied code, which reads and writes interpreter memory, builds a ROP chain,
   and moves the CPU stack into it.
5. **Payload.** The chain calls `GetProcAddress` → `WinExec("calc.exe")`. A real attacker would download and run
   malware instead.

The public demo targets Minecraft 26.3 on Windows x64. However, **the bug is in FreeType's bytecode interpreter,
which runs on every OS.** Only the last step (the ROP gadgets) is Windows-specific, and porting it is ordinary
exploit work. Every Minecraft version since the switch to FreeType uses the same code path. 1.20.x uses
`stb_truetype` instead, whose own documentation says it is not safe for untrusted fonts.

## How FontGuard stops it

FontGuard hooks `TrueTypeGlyphProviderDefinition.load`. That is the one place where Minecraft turns a
resource-pack `.ttf` into a native font, and every TrueType font passes through it before native code sees it.

### Layer 1: Server fonts are blocked

Before the file is opened, FontGuard checks which pack the font *actually* comes from. Server-sent and
downloaded-world packs get the ID `server` (1.20–1.20.2) or `server/<n>/<uuid>` (1.20.3+). The client
assigns these IDs, so a server can't disguise its pack. These fonts are refused with an `IOException`. Minecraft
already handles that safely: it logs the error, skips the font, and keeps using its default font.

### Layer 2: Other fonts are scanned

The exact bytes that would be passed to FreeType are first checked by a pure-Java scanner
(`common/src/main/java/dev/fontguard/scan/TtfScanner.java`). A font is refused if it:

- uses `IDEF` to override a built-in TrueType instruction (the exploit's core trick)
- is malformed: tables or glyph data past the end of the file, a broken `loca` table, truncated bytecode,
  composite glyphs that loop or nest too deeply, or more points/contours than the font's own `maxp` declares
  (a classic heap-overflow setup)
- is not plain TrueType (collections, CFF/OpenType, WOFF). Minecraft doesn't support these anyway, and they would
  reach riskier FreeType parsers.
- is too large (32 MB by default)

### Layer 3: Font bytecode never runs

Every `FT_Load_Glyph` call is rewritten to include `FT_LOAD_NO_HINTING | FT_LOAD_NO_AUTOHINT`, so FreeType's
TrueType bytecode interpreter never runs. This also protects against *future* bugs in the interpreter.

## Limits

- FontGuard covers **fonts**, which is the attack surface minecraft2shell uses. PNG images and sounds in resource
  packs are decoded by other native libraries. No public exploit for those exists today.
- It only protects Fabric/Quilt clients that have it installed. The real fix is for Mojang to update or sandbox
  FreeType.
