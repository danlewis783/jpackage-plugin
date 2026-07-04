package io.github.danlewis783.jpackage;

import io.github.danlewis783.jpackage.internal.OsUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs {@code jpackage --type <installer-type> --app-image ...} to build native installers
 * from a previously created application image. One installer is produced per configured type.
 */
@CacheableTask
public abstract class JPackageInstallerTask extends DefaultTask {

    private static final List<String> INSTALLER_TYPES =
            Arrays.asList("msi", "exe", "pkg", "dmg", "rpm", "deb");

    /** The application image to package, as produced by {@link JPackageImageTask}. */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getAppImageDirectory();

    /** Installer types to produce; empty means the platform default type. */
    @Input
    public abstract ListProperty<String> getTypes();

    @Input
    public abstract Property<String> getAppName();

    @Input
    public abstract Property<String> getAppVersion();

    @Input
    @Optional
    public abstract Property<String> getVendor();

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

    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getResourceDir();

    @Input
    public abstract ListProperty<String> getExtraArgs();

    @Console
    public abstract Property<Boolean> getVerbose();

    /** Launcher of the JDK whose {@code jpackage} tool is invoked. */
    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void createInstallers() {
        File jpackage = OsUtil.jpackageExecutable(getJavaLauncher().get().getExecutablePath().getAsFile());
        if (!jpackage.isFile()) {
            throw new GradleException("jpackage tool not found at " + jpackage
                    + ". Point jpackage.jpackageJdkVersion at a JDK (14+) that includes jpackage.");
        }

        List<String> types = getTypes().get();
        for (String type : types) {
            if (!INSTALLER_TYPES.contains(type)) {
                throw new GradleException("Unsupported installer type '" + type
                        + "'. Supported types: " + INSTALLER_TYPES);
            }
        }
        if (OsUtil.isWindows() && !getAppVersion().get().matches("\\d+(\\.\\d+)*")) {
            throw new GradleException("Windows installers require a numeric version like 1.2.3, but"
                    + " jpackage.appVersion is '" + getAppVersion().get() + "'.");
        }

        File dest = getDestinationDir().get().getAsFile();
        getFileSystemOperations().delete(spec -> spec.delete(dest));
        //noinspection ResultOfMethodCallIgnored
        dest.mkdirs();

        // An empty type list means one run with jpackage's platform default type.
        List<String> runs = types.isEmpty() ? Collections.singletonList(null) : types;
        for (String type : runs) {
            List<String> command = new ArrayList<>();
            command.add(jpackage.getAbsolutePath());
            if (type != null) {
                command.add("--type");
                command.add(type);
            }
            command.add("--app-image");
            command.add(getAppImageDirectory().get().getAsFile().getAbsolutePath());
            command.add("--dest");
            command.add(dest.getAbsolutePath());
            command.add("--name");
            command.add(getAppName().get());
            command.add("--app-version");
            command.add(getAppVersion().get());
            addIfPresent(command, "--vendor", getVendor());
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
            if (getResourceDir().isPresent()) {
                command.add("--resource-dir");
                command.add(getResourceDir().get().getAsFile().getAbsolutePath());
            }
            command.addAll(getExtraArgs().get());
            if (getVerbose().get()) {
                command.add("--verbose");
            }

            getLogger().info("Running: {}", String.join(" ", command));
            getExecOperations().exec(spec -> spec.commandLine(command));
        }

        File[] produced = dest.listFiles();
        if (produced != null) {
            for (File file : produced) {
                getLogger().lifecycle("Created installer: {}", file);
            }
        }
    }

    private static void addIfPresent(List<String> command, String option, Property<String> value) {
        if (value.isPresent()) {
            command.add(option);
            command.add(value.get());
        }
    }

    private static void addFlagIfTrue(List<String> command, String option, Property<Boolean> value) {
        if (value.getOrElse(false)) {
            command.add(option);
        }
    }
}
