# Building from source

You need a JDK (17 or newer) and an internet connection. Gradle downloads the other JDKs it needs
(17, 21 and 25) automatically.

```bash
./gradlew build
```

The jars are created in:
- `legacy/build/libs/`: Minecraft 1.20 – 1.21.11
- `modern/build/libs/`: Minecraft 26.1 and newer

## Project layout

| Folder | What's inside |
|---|---|
| `common/` | Shared code: the font scanner, the mixins, the config, and the scanner tests |
| `legacy/` | Builds the 1.20 – 1.21.11 jar (Mojang names remapped to intermediary) |
| `modern/` | Builds the 26.x jar (unobfuscated) |

## Tests

```bash
./gradlew :core:test
```

To also test against the real exploit pack (if you have a copy of `resource-pack.zip`):

```bash
./gradlew :core:test -Dfontguard.samplePack=/path/to/resource-pack.zip
```

## Running the game for testing

```bash
./gradlew :modern:runClient -Pmodern_minecraft=26.3
```
