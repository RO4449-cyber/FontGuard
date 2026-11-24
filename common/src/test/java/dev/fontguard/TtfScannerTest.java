package dev.fontguard;

import static org.junit.jupiter.api.Assertions.*;

import dev.fontguard.scan.ScanResult;
import dev.fontguard.scan.TtfScanner;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TtfScannerTest {
    private static final long MAX = 32L * 1024 * 1024;

    @Test
    void rejectsGarbage() {
        assertTrue(TtfScanner.scan(new byte[0], MAX).isDangerous());
        assertTrue(TtfScanner.scan(new byte[] {1, 2, 3}, MAX).isDangerous());
        assertTrue(TtfScanner.scan(new byte[64], MAX).isDangerous());
    }

    @Test
    void rejectsNonTrueTypeContainers() {
        for (String sig : new String[] {"OTTO", "ttcf", "wOFF", "wOF2"}) {
            byte[] data = new byte[64];
            System.arraycopy(sig.getBytes(), 0, data, 0, 4);
            ScanResult r = TtfScanner.scan(data, MAX);
            assertTrue(r.isDangerous(), sig);
        }
    }

    @Test
    void rejectsOversized() {
        byte[] data = new byte[2048];
        ByteBuffer.wrap(data).putInt(0x00010000);
        assertTrue(TtfScanner.scan(data, 1024).isDangerous());
    }

    @Test
    void doesNotMoveBufferPosition() {
        ByteBuffer buf = ByteBuffer.allocate(100);
        buf.position(37);
        TtfScanner.scan(buf, MAX);
        assertEquals(37, buf.position());
    }

    /**
     * Run with -Dfontguard.samplePack=/path/to/minecraft2shell/resource-pack.zip
     * to verify every exploit font in the real pack is caught.
     */
    @Test
    void catchesMinecraft2ShellPack() throws IOException {
        String path = System.getProperty("fontguard.samplePack", "");
        Assumptions.assumeTrue(!path.isEmpty() && Files.exists(Paths.get(path)), "sample pack not provided");
        int fonts = 0;
        try (ZipFile zip = new ZipFile(path)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.getName().endsWith(".ttf")) continue;
                fonts++;
                byte[] data = zip.getInputStream(e).readAllBytes();
                ScanResult r = TtfScanner.scan(data, MAX);
                if (fonts == 1) System.out.println(e.getName() + " -> " + r);
                assertTrue(r.isDangerous(), e.getName() + " was not flagged: " + r);
            }
        }
        System.out.println("Flagged all " + fonts + " exploit fonts");
        assertTrue(fonts > 0);
    }

    /** False-positive check: real system fonts should pass. */
    @Test
    void acceptsRealSystemFonts() throws IOException {
        List<Path> fonts = new ArrayList<>();
        for (String dir : new String[] {"/System/Library/Fonts", "/Library/Fonts", "C:\\Windows\\Fonts",
                "/usr/share/fonts", System.getProperty("user.home") + "/Library/Fonts"}) {
            Path d = Paths.get(dir);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> s = Files.walk(d)) {
                s.filter(p -> p.toString().toLowerCase().endsWith(".ttf")).forEach(fonts::add);
            }
        }
        Assumptions.assumeTrue(!fonts.isEmpty(), "no system fonts found");
        int flagged = 0, tt = 0;
        for (Path p : fonts) {
            byte[] data = Files.readAllBytes(p);
            int sig = data.length >= 4 ? ByteBuffer.wrap(data).getInt() : 0;
            if (sig != 0x00010000 && sig != 0x74727565) continue; // skip CFF-in-.ttf etc.
            tt++;
            ScanResult r = TtfScanner.scan(data, 64L * 1024 * 1024);
            if (r.isDangerous()) {
                flagged++;
                System.out.println("FLAGGED " + p + ": " + r.dangers());
            }
        }
        System.out.println("System TrueType fonts scanned: " + tt + ", flagged: " + flagged);
        assertTrue(flagged * 20 <= tt, "too many false positives: " + flagged + "/" + tt);
    }
}
