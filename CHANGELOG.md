# Changelog

## 1.0.0
First release.
- Blocks fonts from server and world resource packs
- Scans fonts from local packs and mods (catches all 144 minecraft2shell fonts)
- Disables TrueType hinting so font bytecode never runs
- Chat warning when a font is blocked, settings in `config/fontguard.properties`
- Supports Minecraft 1.20 – 1.21.11 and 26.1+ on Fabric/Quilt (Windows, macOS, Linux)
- Tested in Minecraft 26.3 against the real exploit pack sent by a server
