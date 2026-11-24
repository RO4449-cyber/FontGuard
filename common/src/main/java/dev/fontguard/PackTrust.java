package dev.fontguard;

/** Decides where a resource pack came from, based on the pack id Minecraft assigns it. */
public final class PackTrust {
    private PackTrust() {}

    /**
     * Server-sent and downloaded-world packs are always named {@code server} (1.20.1-1.20.2)
     * or {@code server/<n>/<uuid>} (1.20.3+). The id is assigned by the client, not the server,
     * so a server cannot disguise its pack.
     */
    public static boolean isServerPack(String packId) {
        return packId == null || packId.equals("server") || packId.startsWith("server/")
                || packId.equals("world") || packId.startsWith("world/");
    }
}
