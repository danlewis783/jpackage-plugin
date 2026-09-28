package io.github.danlewis783.jpackage.internal;

import java.io.File;
import java.util.Locale;

/** Small OS helpers shared by the jpackage tasks. Not public API. */
public final class OsUtil {

    private OsUtil() {
    }

    public static boolean isWindows() {
        return osName().contains("windows");
    }

    public static boolean isMacOs() {
        String os = osName();
        return os.contains("mac") || os.contains("darwin");
    }

    /** OS family and architecture, e.g. {@code windows/amd64}; identifies platform-specific outputs. */
    public static String platform() {
        String family = isWindows() ? "windows" : isMacOs() ? "macos" : osName();
        return family + "/" + System.getProperty("os.arch", "");
    }

    /** Resolves the jpackage executable that lives next to the given {@code java} executable. */
    public static File jpackageExecutable(File javaExecutable) {
        return new File(javaExecutable.getParentFile(), isWindows() ? "jpackage.exe" : "jpackage");
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
