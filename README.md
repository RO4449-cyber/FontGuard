<div align="center">

<img src="docs/logo.png" alt="FontGuard logo" width="160">

# FontGuard

**Anti-RCE protection for Minecraft resource packs**

Stops malicious fonts in server resource packs from taking over your computer,
like the [minecraft2shell](https://github.com/bikini/minecraft2shell) exploit.

[![Download](https://img.shields.io/github/v/release/RO4449-cyber/FontGuard?label=download&color=2868d6)](https://github.com/RO4449-cyber/FontGuard/releases/latest)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20%20–%2026.x-3a8a3a)
![Loader](https://img.shields.io/badge/loader-Fabric%20%7C%20Quilt-dbb47a)
![Platforms](https://img.shields.io/badge/Windows%20%7C%20macOS%20%7C%20Linux-supported-555)
![License](https://img.shields.io/badge/license-non--commercial-f2b632)

</div>

---

## ⚠️ Why you need this

In November 2026, a public proof of concept showed that **a Minecraft server can run its own program on your
computer** just by sending you a resource pack. The pack contains a booby-trapped font. When Minecraft draws
text with it, a bug in the game's font engine (FreeType) lets the font take control of the game.
An attacker could use this to install a virus or a remote-access trojan (RAT).

FontGuard blocks this attack and others like it. [Read how the exploit works →](docs/HOW-IT-WORKS.md)

## 🛡️ What FontGuard does

| Layer | What it does | Default |
|---|---|---|
| **1. Block server fonts** | Font files sent by servers or downloaded worlds are never loaded. The rest of the pack (textures, sounds) still works. | On |
| **2. Scan other fonts** | Fonts from your own packs and mods are checked first. Malformed or exploit-style fonts are refused. | On |
| **3. Turn off font code** | Fonts can contain small programs ("hinting"). FontGuard stops them from ever running, which is where this bug lives. | On |

Each layer alone stops minecraft2shell. When something is blocked, you'll see a gold message in chat.

## 📥 Install

1. Install [Fabric](https://fabricmc.net/use/installer/) (or Quilt) for your Minecraft version.
2. Download the right jar from [**Releases**](https://github.com/RO4449-cyber/FontGuard/releases/latest):

   | Your Minecraft version | Download |
   |---|---|
   | **1.20 to 1.21.11** | `fontguard-fabric-1.20-1.21.11-<version>.jar` |
   | **26.1 and newer** | `fontguard-fabric-26.x-<version>.jar` |

3. Put it in your `.minecraft/mods` folder. That's it.

No Fabric API is needed. It only runs on your game (the client), so servers don't need it.

## ✅ Tested

Tested in Minecraft 26.3 with the real exploit pack:

- **Server attack (worst case, no prompt):** all 144 malicious fonts were blocked, the player joined
  normally, and a warning appeared in chat.
- **Same pack installed by hand:** all 144 fonts were caught by the scanner.
- **Normal custom fonts** (for example Arial) still load and look fine.
- **False alarms:** 203 of 204 real macOS fonts pass the scanner. The one it refuses can't be used by
  Minecraft anyway.

## ⚙️ Settings

After the first launch, settings are in `config/fontguard.properties`:

| Setting | Options | Default |
|---|---|---|
| `server_pack_fonts` | `BLOCK`, `SCAN` | `BLOCK` |
| `local_pack_fonts` | `SCAN`, `BLOCK`, `ALLOW` | `SCAN` |
| `disable_font_hinting` | `true`, `false` | `true` |
| `notify_in_chat` | `true`, `false` | `true` |
| `max_font_size_mb` | number | `32` |

The defaults are the safest choice. For safety, server fonts can never be set to `ALLOW`.

## ❓ FAQ

**Will this change how my game looks?**
No. Minecraft's own fonts aren't TrueType fonts, so they are never touched. A server that uses a custom
`.ttf` font will show the normal Minecraft font instead.

**Does it make me 100% safe?**
No mod can promise that. FontGuard closes the font attack, which is the one used by the public exploit.
Resource packs also contain images and sounds, which are handled by other code. If you don't trust a
server, decline its resource pack.

**Is there a Forge / NeoForge version?**
Not yet. It's planned.

**I found a security problem.**
Please see [SECURITY.md](SECURITY.md).

## 📚 More

- [How the exploit works and how FontGuard stops it](docs/HOW-IT-WORKS.md)
- [Building from source](docs/BUILDING.md)
- [Changelog](CHANGELOG.md)

## 👥 Developers

- **RO4449**
- **Claude** (Anthropic)

## 📄 License

FontGuard Non-Commercial License. See [LICENSE](LICENSE).
The source code is open: use it, fork it, and improve it. **You may not make money from it.** That
means no selling, no paywalls, and no monetized re-uploads.
