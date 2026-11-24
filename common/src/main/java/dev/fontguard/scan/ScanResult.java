package dev.fontguard.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of {@link TtfScanner#scan}. A font is only considered safe when it has no dangers. */
public final class ScanResult {
    private final List<String> dangers = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    void danger(String msg) { dangers.add(msg); }
    void warn(String msg) { warnings.add(msg); }

    public boolean isDangerous() { return !dangers.isEmpty(); }
    public List<String> dangers() { return Collections.unmodifiableList(dangers); }
    public List<String> warnings() { return Collections.unmodifiableList(warnings); }

    /** First danger, for short messages. */
    public String summary() {
        if (dangers.isEmpty()) return "no problems found";
        return dangers.get(0) + (dangers.size() > 1 ? " (+" + (dangers.size() - 1) + " more)" : "");
    }

    @Override
    public String toString() {
        return "ScanResult{dangers=" + dangers + ", warnings=" + warnings + "}";
    }
}
