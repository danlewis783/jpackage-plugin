package io.github.danlewis783.jpackage;

import io.github.danlewis783.jpackage.internal.OsUtil;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs {@code jpackage --type <installer-type> --app-image ...} to build native installers
 * from a previously created application image. One installer is produced per configured type.
 */
@CacheableTask
public abstract class JPackageInstallerTask extends AbstractJPackageTask {

    /** The application image to package, as produced by {@link JPackageImageTask}. */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getAppImageDirectory();

    /** Installer types to produce; empty means the platform default type. */
    @Input
    public abstract ListProperty<String> getTypes();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getLicenseFile();

    @Input
    @Optional
    public abstract Property<String> getAboutUrl();

    @Input
    @Optional
    public abstract Property<String> getInstallDir();

    @Input
    @Optional
    public abstract Property<Boolean> getWinMenu();

    @Input
    @Optional
    public abstract Property<String> getWinMenuGroup();

    @Input
    @Optional
    public abstract Property<Boolean> getWinShortcut();

    @Input
    @Optional
    public abstract Property<Boolean> getWinShortcutPrompt();

    @Input
    @Optional
    public abstract Property<Boolean> getWinDirChooser();

    @Input
    @Optional
    public abstract Property<Boolean> getWinPerUserInstall();

    @Input
    @Optional
    public abstract Property<String> getWinUpgradeUuid();

    @TaskAction
    public void createInstallers() {
        File jpackage = resolveJpackage();
        validate();

        File dest = getDestinationDir().get().getAsFile();
        getFileSystemOperations().delete(spec -> spec.delete(dest));
        //noinspection ResultOfMethodCallIgnored
        dest.mkdirs();

        // An empty type list means one run with jpackage's platform default type.
        List<String> types = getTypes().get();
        List<String> runs = types.isEmpty() ? Collections.singletonList(null) : types;
        for (String type : runs) {
            runJpackage(buildCommand(jpackage, type));
        }

        File[] produced = dest.listFiles();
        if (produced != null) {
            for (File file : produced) {
                getLogger().lifecycle("Created installer: {}", file);
            }
        }
    }

    /** Fails on installer types the current OS cannot build and on versions Windows installers reject. */
    void validate() {
        List<String> supported = supportedTypes();
        for (String type : getTypes().get()) {
            if (!supported.contains(type)) {
                throw new GradleException("Unsupported installer type '" + type
                        + "' on this OS. Supported types: " + supported);
            }
        }
        if (OsUtil.isWindows()) {
            checkMsiVersion(getAppVersion().get());
        }
    }

    /** The jpackage command for one installer type, without extra arguments and {@code --verbose}. */
    List<String> buildCommand(File jpackage, String type) {
        List<String> command = newCommand(jpackage, type);
        command.add("--app-image");
        command.add(getAppImageDirectory().get().getAsFile().getAbsolutePath());
        addIfPresent(command, "--about-url", getAboutUrl());
        addIfPresent(command, "--install-dir", getInstallDir());
        if (getLicenseFile().isPresent()) {
            command.add("--license-file");
            command.add(getLicenseFile().get().getAsFile().getAbsolutePath());
        }
        if (OsUtil.isWindows()) {
            addFlagIfTrue(command, "--win-menu", getWinMenu());
            addIfPresent(command, "--win-menu-group", getWinMenuGroup());
            addFlagIfTrue(command, "--win-shortcut", getWinShortcut());
            addFlagIfTrue(command, "--win-shortcut-prompt", getWinShortcutPrompt());
            addFlagIfTrue(command, "--win-dir-chooser", getWinDirChooser());
            addFlagIfTrue(command, "--win-per-user-install", getWinPerUserInstall());
            addIfPresent(command, "--win-upgrade-uuid", getWinUpgradeUuid());
        }
        return command;
    }

    private static List<String> supportedTypes() {
        if (OsUtil.isWindows()) {
            return Arrays.asList("msi", "exe");
        }
        if (OsUtil.isMacOs()) {
            return Arrays.asList("pkg", "dmg");
        }
        return Arrays.asList("rpm", "deb");
    }

    /** MSI product version rules, as enforced by jpackage for {@code msi} and {@code exe}. */
    private static void checkMsiVersion(String version) {
        String[] parts = version.split("\\.");
        boolean valid = version.matches("\\d+(\\.\\d+){1,3}")
                && new BigInteger(parts[0]).compareTo(BigInteger.valueOf(255)) <= 0
                && new BigInteger(parts[1]).compareTo(BigInteger.valueOf(255)) <= 0
                && (parts.length < 3 || new BigInteger(parts[2]).compareTo(BigInteger.valueOf(65535)) <= 0);
        if (!valid) {
            throw new GradleException("Windows installers require a version of 2 to 4 numeric components"
                    + " (major and minor at most 255, build at most 65535) like 1.2.3, but"
                    + " jpackage.appVersion is '" + version + "'.");
        }
    }

    private static void addFlagIfTrue(List<String> command, String option, Property<Boolean> value) {
        if (value.getOrElse(false)) {
            command.add(option);
        }
    }
}
