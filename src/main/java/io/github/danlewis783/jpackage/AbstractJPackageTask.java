package io.github.danlewis783.jpackage;

import io.github.danlewis783.jpackage.internal.OsUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Properties and helpers shared by the tasks that invoke {@code jpackage}.
 */
@DisableCachingByDefault(because = "Abstract base type; the concrete tasks declare their own cacheability")
public abstract class AbstractJPackageTask extends DefaultTask {

    @Input
    public abstract Property<String> getAppName();

    @Input
    public abstract Property<String> getAppVersion();

    @Input
    @Optional
    public abstract Property<String> getVendor();

    @Input
    @Optional
    public abstract Property<String> getAppDescription();

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

    /** OS family and architecture; jpackage output is platform-specific, so it keys the build cache. */
    @Input
    public String getPlatform() {
        return OsUtil.platform();
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    /** Resolves the {@code jpackage} executable of the configured toolchain, failing if it is missing. */
    protected File resolveJpackage() {
        File jpackage = OsUtil.jpackageExecutable(getJavaLauncher().get().getExecutablePath().getAsFile());
        if (!jpackage.isFile()) {
            throw new GradleException("jpackage tool not found at " + jpackage
                    + ". Point jpackage.jpackageJdkVersion at a JDK (14+) that includes jpackage.");
        }
        return jpackage;
    }

    /** jpackage on Windows rejects versions with non-numeric components, e.g. {@code 1.0-SNAPSHOT}. */
    protected void checkNumericVersionOnWindows() {
        String version = getAppVersion().get();
        if (OsUtil.isWindows() && !version.matches("\\d+(\\.\\d+)*")) {
            throw new GradleException("jpackage on Windows requires a numeric version like 1.2.3, but"
                    + " jpackage.appVersion is '" + version + "'. Set jpackage.appVersion explicitly,"
                    + " e.g. to the project version without its -SNAPSHOT suffix.");
        }
    }

    /**
     * Starts a jpackage command line with the options common to all invocations.
     *
     * @param type the {@code --type} value, or {@code null} for jpackage's platform default
     */
    protected List<String> newCommand(File jpackage, String type) {
        List<String> command = new ArrayList<>();
        command.add(jpackage.getAbsolutePath());
        if (type != null) {
            command.add("--type");
            command.add(type);
        }
        command.add("--name");
        command.add(getAppName().get());
        command.add("--app-version");
        command.add(getAppVersion().get());
        command.add("--dest");
        command.add(getDestinationDir().get().getAsFile().getAbsolutePath());
        addIfPresent(command, "--vendor", getVendor());
        addIfPresent(command, "--description", getAppDescription());
        if (getResourceDir().isPresent()) {
            command.add("--resource-dir");
            command.add(getResourceDir().get().getAsFile().getAbsolutePath());
        }
        return command;
    }

    /** Appends the extra arguments and {@code --verbose}, then runs the jpackage command. */
    protected void runJpackage(List<String> command) {
        command.addAll(getExtraArgs().get());
        if (getVerbose().get()) {
            command.add("--verbose");
        }
        getLogger().info("Running: {}", String.join(" ", command));
        getExecOperations().exec(spec -> spec.commandLine(command));
    }

    protected static void addIfPresent(List<String> command, String option, Property<String> value) {
        if (value.isPresent()) {
            command.add(option);
            command.add(value.get());
        }
    }
}
