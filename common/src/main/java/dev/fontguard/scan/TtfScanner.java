package dev.fontguard.scan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static, pure-Java safety scanner for TrueType font files.
 *
 * <p>It never hands the font to native code. It reads the file the way FreeType would and
 * rejects anything that is malformed or that uses TrueType features abused by font exploits
 * such as minecraft2shell (CVE-style memory corruption in the FreeType bytecode interpreter).
 *
 * <p>All reads are bounds-checked. Any parse error is itself treated as dangerous, because a
 * font that confuses this parser could also confuse FreeType's.
 */
public final class TtfScanner {

    /** Hard cap on nested composite glyphs (FreeType's own limit is similar). */
    private static final int MAX_COMPOSITE_DEPTH = 16;
    private static final int MAX_TABLES = 64;

    private TtfScanner() {}

    public static ScanResult scan(byte[] data, long maxBytes) {
        return scan(ByteBuffer.wrap(data), maxBytes);
    }

    /** Scans {@code buf} from position 0 up to its limit. The buffer's position/limit are not changed. */
    public static ScanResult scan(ByteBuffer buf, long maxBytes) {
        ScanResult result = new ScanResult();
        ByteBuffer b = buf.duplicate().order(ByteOrder.BIG_ENDIAN);
        b.position(0);
        try {
            new Parser(b, result, maxBytes).run();
        } catch (MalformedException | IndexOutOfBoundsException | IllegalArgumentException e) {
            result.danger("malformed font structure: " + e.getMessage());
        }
        return result;
    }

    private static final class MalformedException extends RuntimeException {
        MalformedException(String msg) { super(msg); }
    }

    private static final class Table {
        final int offset, length;
        Table(int offset, int length) { this.offset = offset; this.length = length; }
    }

    private static final class Parser {
        private final ByteBuffer b;
        private final ScanResult r;
        private final long maxBytes;
        private final int size;
        private final Map<String, Table> tables = new HashMap<>();

        // maxp values
        private int numGlyphs, maxPoints, maxContours, maxCompositePoints, maxCompositeContours,
                maxInstructionDefs, maxStackElements, maxStorage, maxComponentDepth, maxSizeOfInstructions;
        private int[] loca;
        private Table glyf;
        // per-glyph info for composite analysis
        private int[] simplePoints, simpleContours;
        private List<int[]> components; // index -> child glyph ids (null for simple/empty)

        Parser(ByteBuffer b, ScanResult r, long maxBytes) {
            this.b = b;
            this.r = r;
            this.maxBytes = maxBytes;
            this.size = b.limit();
        }

        void run() {
            if (size > maxBytes) {
                r.danger("font file is too large (" + size + " bytes)");
                return;
            }
            if (size < 12) throw new MalformedException("file too small");

            int version = b.getInt(0);
            if (version == 0x74746366) { // 'ttcf'
                r.danger("font collections (.ttc) are not used by Minecraft and are refused");
                return;
            }
            if (version == 0x4F54544F) { // 'OTTO'
                r.danger("CFF/OpenType outlines are not used by Minecraft and are refused");
                return;
            }
            if (version == 0x774F4646 || version == 0x774F4632) { // wOFF / wOF2
                r.danger("WOFF fonts are not used by Minecraft and are refused");
                return;
            }
            if (version != 0x00010000 && version != 0x74727565) { // 1.0 / 'true'
                r.danger(String.format("not a TrueType font (signature 0x%08X)", version));
                return;
            }

            readDirectory();
            for (String req : new String[] {"head", "maxp", "cmap", "loca", "glyf", "hhea", "hmtx"}) {
                if (!tables.containsKey(req)) throw new MalformedException("missing required table '" + req + "'");
            }
            readMaxp();
            readLoca();
            glyf = tables.get("glyf");

            Table fpgm = tables.get("fpgm");
            if (fpgm != null) checkProgram("fpgm", fpgm.offset, fpgm.length, true);
            Table prep = tables.get("prep");
            if (prep != null) checkProgram("prep", prep.offset, prep.length, true);

            readGlyphs();
            checkComposites();

            if (maxInstructionDefs > 0) {
                r.warn("font declares instruction definitions (maxp.maxInstructionDefs=" + maxInstructionDefs + ")");
            }
            if (maxStackElements > 4096) {
                r.warn("very large hinting stack requested (maxp.maxStackElements=" + maxStackElements + ")");
            }
            if (maxStorage > 4096) {
                r.warn("very large hinting storage requested (maxp.maxStorage=" + maxStorage + ")");
            }
        }

        private void readDirectory() {
            int numTables = u16(4);
            if (numTables == 0 || numTables > MAX_TABLES) throw new MalformedException("bad table count " + numTables);
            int dirEnd = 12 + numTables * 16;
            if (dirEnd > size) throw new MalformedException("table directory runs past end of file");
            for (int i = 0; i < numTables; i++) {
                int rec = 12 + i * 16;
                String tag = tag(rec);
                long off = u32(rec + 8);
                long len = u32(rec + 12);
                if (off < dirEnd && len > 0) throw new MalformedException("table '" + tag + "' overlaps the directory");
                if (off + len > size) throw new MalformedException("table '" + tag + "' runs past end of file");
                if (tables.put(tag, new Table((int) off, (int) len)) != null) {
                    throw new MalformedException("duplicate table '" + tag + "'");
                }
            }
        }

        private void readMaxp() {
            Table t = tables.get("maxp");
            if (t.length < 6) throw new MalformedException("maxp too short");
            long ver = u32(t.offset);
            numGlyphs = u16(t.offset + 4);
            if (numGlyphs == 0) throw new MalformedException("font has no glyphs");
            if (ver != 0x00010000L) throw new MalformedException("TrueType maxp must be version 1.0");
            if (t.length < 32) throw new MalformedException("maxp 1.0 too short");
            int o = t.offset;
            maxPoints = u16(o + 6);
            maxContours = u16(o + 8);
            maxCompositePoints = u16(o + 10);
            maxCompositeContours = u16(o + 12);
            maxStorage = u16(o + 18);
            maxInstructionDefs = u16(o + 22);
            maxStackElements = u16(o + 24);
            maxSizeOfInstructions = u16(o + 26);
            maxComponentDepth = u16(o + 30);
        }

        private void readLoca() {
            Table head = tables.get("head");
            if (head.length < 54) throw new MalformedException("head too short");
            int format = s16(head.offset + 50);
            Table t = tables.get("loca");
            Table g = tables.get("glyf");
            loca = new int[numGlyphs + 1];
            int entry = format == 0 ? 2 : format == 1 ? 4 : -1;
            if (entry < 0) throw new MalformedException("bad indexToLocFormat " + format);
            if ((long) (numGlyphs + 1) * entry > t.length) throw new MalformedException("loca too short for glyph count");
            long prev = 0;
            for (int i = 0; i <= numGlyphs; i++) {
                long v = format == 0 ? (long) u16(t.offset + i * 2) * 2 : u32(t.offset + i * 4);
                if (v < prev) throw new MalformedException("loca offsets go backwards at glyph " + i);
                if (v > g.length) throw new MalformedException("loca offset past end of glyf at glyph " + i);
                loca[i] = (int) v;
                prev = v;
            }
        }

        private void readGlyphs() {
            simplePoints = new int[numGlyphs];
            simpleContours = new int[numGlyphs];
            components = new ArrayList<>(numGlyphs);
            int worstPoints = 0, worstContours = 0;
            for (int gid = 0; gid < numGlyphs; gid++) {
                components.add(null);
                int start = glyf.offset + loca[gid];
                int end = glyf.offset + loca[gid + 1];
                if (end == start) continue; // empty glyph
                if (end - start < 10) throw new MalformedException("glyph " + gid + " header truncated");
                int nContours = s16(start);
                int p = start + 10;
                if (nContours >= 0) {
                    int points = 0;
                    int last = -1;
                    for (int c = 0; c < nContours; c++) {
                        need(p + 2, end, gid);
                        int ep = u16(p);
                        if (ep <= last) {
                            throw new MalformedException("glyph " + gid + " contour end points not increasing");
                        }
                        last = ep;
                        p += 2;
                    }
                    points = nContours == 0 ? 0 : last + 1;
                    need(p + 2, end, gid);
                    int insLen = u16(p);
                    p += 2;
                    need(p + insLen, end, gid);
                    checkProgram("glyph " + gid, p, insLen, false);
                    p += insLen;
                    p = skipSimpleOutline(p, end, points, gid);
                    simplePoints[gid] = points;
                    simpleContours[gid] = nContours;
                    worstPoints = Math.max(worstPoints, points);
                    worstContours = Math.max(worstContours, nContours);
                } else if (nContours == -1) {
                    List<Integer> kids = new ArrayList<>();
                    boolean more = true;
                    boolean haveInstructions = false;
                    while (more) {
                        need(p + 4, end, gid);
                        int flags = u16(p);
                        int child = u16(p + 2);
                        p += 4;
                        if (child >= numGlyphs) {
                            throw new MalformedException("glyph " + gid + " references missing glyph " + child);
                        }
                        kids.add(child);
                        p += (flags & 0x0001) != 0 ? 4 : 2;             // ARG_1_AND_2_ARE_WORDS
                        if ((flags & 0x0008) != 0) p += 2;               // WE_HAVE_A_SCALE
                        else if ((flags & 0x0040) != 0) p += 4;          // X_AND_Y_SCALE
                        else if ((flags & 0x0080) != 0) p += 8;          // TWO_BY_TWO
                        need(p, end, gid);
                        if ((flags & 0x0100) != 0) haveInstructions = true; // WE_HAVE_INSTRUCTIONS
                        more = (flags & 0x0020) != 0;                     // MORE_COMPONENTS
                        if (kids.size() > 1024) throw new MalformedException("glyph " + gid + " has too many components");
                    }
                    if (haveInstructions) {
                        need(p + 2, end, gid);
                        int insLen = u16(p);
                        p += 2;
                        need(p + insLen, end, gid);
                        checkProgram("composite glyph " + gid, p, insLen, false);
                    }
                    int[] arr = new int[kids.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = kids.get(i);
                    components.set(gid, arr);
                } else {
                    throw new MalformedException("glyph " + gid + " has invalid contour count " + nContours);
                }
            }
            if (worstPoints > maxPoints) {
                r.danger("a glyph has " + worstPoints + " points but maxp only allows " + maxPoints
                        + " (allocation-size mismatch, a classic heap-overflow trigger)");
            }
            if (worstContours > maxContours) {
                r.danger("a glyph has " + worstContours + " contours but maxp only allows " + maxContours);
            }
        }

        /** Walks the flag/coordinate arrays of a simple glyph to make sure they fit. */
        private int skipSimpleOutline(int p, int end, int points, int gid) {
            int xBytes = 0, yBytes = 0;
            int i = 0;
            while (i < points) {
                need(p + 1, end, gid);
                int f = u8(p++);
                int repeat = 1;
                if ((f & 0x08) != 0) {
                    need(p + 1, end, gid);
                    repeat += u8(p++);
                }
                int xs = (f & 0x02) != 0 ? 1 : ((f & 0x10) != 0 ? 0 : 2);
                int ys = (f & 0x04) != 0 ? 1 : ((f & 0x20) != 0 ? 0 : 2);
                xBytes += xs * repeat;
                yBytes += ys * repeat;
                i += repeat;
            }
            if (i > points) {
                // Flag repeat count overruns the point array. FreeType clamps this, but it is a malformation.
                r.warn("glyph " + gid + " flag repeat count overruns its point count");
            }
            need(p + xBytes + yBytes, end, gid);
            return p + xBytes + yBytes;
        }

        /** DFS over composite glyphs: cycles, depth and total point counts. */
        private void checkComposites() {
            int n = numGlyphs;
            int[] state = new int[n];        // 0 = unvisited, 1 = in progress, 2 = done
            int[] depth = new int[n];
            long[] points = new long[n];
            long[] contours = new long[n];
            int worstDepth = 0;
            long worstPoints = 0, worstContours = 0;
            for (int g = 0; g < n; g++) {
                if (components.get(g) == null) continue;
                visit(g, state, depth, points, contours, 0);
                worstDepth = Math.max(worstDepth, depth[g]);
                worstPoints = Math.max(worstPoints, points[g]);
                worstContours = Math.max(worstContours, contours[g]);
            }
            if (worstDepth > Math.max(maxComponentDepth, 1) + 1) {
                r.danger("composite glyphs nest " + worstDepth + " levels deep but maxp declares "
                        + maxComponentDepth);
            }
            if (worstPoints > maxCompositePoints && worstPoints > maxPoints) {
                r.danger("composite glyph has " + worstPoints + " points but maxp declares "
                        + maxCompositePoints + " (allocation-size mismatch)");
            }
            if (worstContours > maxCompositeContours && worstContours > maxContours) {
                r.danger("composite glyph has " + worstContours + " contours but maxp declares "
                        + maxCompositeContours);
            }
        }

        private void visit(int g, int[] state, int[] depth, long[] points, long[] contours, int level) {
            if (level > MAX_COMPOSITE_DEPTH) throw new MalformedException("composite glyphs nested too deeply");
            if (state[g] == 2) return;
            if (state[g] == 1) throw new MalformedException("composite glyph " + g + " references itself (cycle)");
            int[] kids = components.get(g);
            if (kids == null) {
                state[g] = 2;
                points[g] = simplePoints[g];
                contours[g] = simpleContours[g];
                return;
            }
            state[g] = 1;
            int d = 0;
            long pts = 0, cts = 0;
            for (int k : kids) {
                visit(k, state, depth, points, contours, level + 1);
                d = Math.max(d, depth[k]);
                pts += points[k];
                cts += contours[k];
            }
            depth[g] = d + 1;
            points[g] = pts;
            contours[g] = cts;
            state[g] = 2;
        }

        /**
         * Walks a TrueType bytecode program, skipping inline push data, and flags opcodes that
         * legitimate fonts don't need but exploits do.
         */
        private void checkProgram(String where, int off, int len, boolean definitionsAllowed) {
            if (len > maxSizeOfInstructions && !definitionsAllowed) {
                r.warn(where + " has " + len + " bytes of instructions, more than maxp declares");
            }
            int p = off, end = off + len;
            int lastPushed = -1; // value on top of the stack if the previous instruction was a push
            while (p < end) {
                int op = u8(p++);
                int skip = 0;
                boolean words = false;
                if (op == 0x40) {               // NPUSHB
                    if (p >= end) throw new MalformedException(where + ": truncated NPUSHB");
                    skip = u8(p++);
                } else if (op == 0x41) {        // NPUSHW
                    if (p >= end) throw new MalformedException(where + ": truncated NPUSHW");
                    skip = u8(p++) * 2;
                    words = true;
                } else if (op >= 0xB0 && op <= 0xB7) { // PUSHB[n]
                    skip = op - 0xAF;
                } else if (op >= 0xB8 && op <= 0xBF) { // PUSHW[n]
                    skip = (op - 0xB7) * 2;
                    words = true;
                } else if (op == 0x89) {        // IDEF
                    if (!definitionsAllowed) {
                        r.danger(where + ": IDEF inside a glyph program (not allowed by the TrueType spec)");
                    } else if (lastPushed < 0 || isStandardOpcode(lastPushed)) {
                        r.danger(where + ": IDEF overrides " + (lastPushed < 0 ? "an unknown opcode"
                                : String.format("built-in opcode 0x%02X", lastPushed))
                                + "; this is the technique used by the minecraft2shell exploit");
                    } else {
                        r.warn(where + String.format(": IDEF for undefined opcode 0x%02X", lastPushed));
                    }
                } else if (op == 0x2C && !definitionsAllowed) { // FDEF outside fpgm/prep
                    r.danger(where + ": function definition inside a glyph program (not allowed by the TrueType spec)");
                }
                if (p + skip > end) throw new MalformedException(where + ": push data runs past end of program");
                if (skip > 0) {
                    lastPushed = words ? (b.getShort(p + skip - 2) & 0xFFFF) : u8(p + skip - 1);
                } else {
                    lastPushed = -1;
                }
                p += skip;
            }
        }

        /** Opcodes the TrueType interpreter already implements; redefining one is never legitimate. */
        private static boolean isStandardOpcode(int op) {
            if (op < 0 || op > 0xFF) return false;
            if (op == 0x7B || op == 0x83 || op == 0x84 || op == 0x8F || op == 0x90) return false;
            // 0x91 GETVARIATION / 0x92 GETDATA were added late; older Apple fonts legitimately IDEF them.
            return op <= 0x8E || op >= 0xB0;
        }

        // ---- bounds-checked readers ----
        private void need(int upTo, int end, int gid) {
            if (upTo > end) throw new MalformedException("glyph " + gid + " data runs past its end");
        }
        private int u8(int at) { return b.get(at) & 0xFF; }
        private int u16(int at) { return b.getShort(at) & 0xFFFF; }
        private int s16(int at) { return b.getShort(at); }
        private long u32(int at) { return b.getInt(at) & 0xFFFFFFFFL; }
        private String tag(int at) {
            char[] c = new char[4];
            for (int i = 0; i < 4; i++) c[i] = (char) u8(at + i);
            return new String(c);
        }
    }
}
