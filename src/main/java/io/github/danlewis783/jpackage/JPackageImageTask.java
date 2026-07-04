package io.github.danlewis783.jpackage;

import io.github.danlewis783.jpackage.internal.OsUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
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
import java.util.List;

/**
 * Runs {@code jpackage --type app-image} to produce a self-contained application image
 * (native launcher plus bundled Java runtime), then copies the configured documentation
 * files into the image root.
 */
@CacheableTask
public abstract class JPackageImageTask extends DefaultTask {

    /** Jars packaged as the application: the main jar and its runtime classpath. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getAppContent();

    @Input
    public abstract Property<String> getAppName();

    @Input
    public abstract Property<String> getAppVersion();

    @Input
    public abstract Property<String> getMainClass();

    /** File name of the main jar within {@link #getAppContent()}. */
    @Input
    public abstract Property<String> getMainJarName();

    @Input
    @Optional
    public abstract Property<String> getVendor();

    @Input
    @Optional
    public abstract Property<String> getAppDescription();

    @Input
    @Optional
    public abstract Property<String> getCopyright();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getIcon();

    @Input
    public abstract Property<Boolean> getWinConsole();

    @Input
    public abstract ListProperty<String> getJavaOptions();

    @Input
    public abstract ListProperty<String> getAppArguments();

    /** Files copied into the root of the application image after jpackage runs. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDocFiles();

    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getRuntimeImage();

    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getResourceDir();

    @Input
    public abstract ListProperty<String> getAddModules();

    @Input
    public abstract ListProperty<String> getJlinkOptions();

    @Input
    public abstract ListProperty<String> getExtraArgs();

    @Console
    public abstract Property<Boolean> getVerbose();

    /** Launcher of the JDK whose {@code jpackage} tool is invoked. */
    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /** Directory the app image is created in; the image itself is {@code <dest>/<appName>}. */
    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    /** The application image directory produced by this task. */
    @Internal
    public Provider<Directory> getAppImageDirectory() {
        return getDestinationDir().dir(getAppName().map(name -> OsUtil.isMacOs() ? name + ".app" : name));
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void createAppImage() {
        File jpackage = OsUtil.jpackageExecutable(getJavaLauncher().get().getExecutablePath().getAsFile());
        if (!jpackage.isFile()) {
            throw new GradleException("jpackage tool not found at " + jpackage
                    + ". Point jpackage.jpackageJdkVersion at a JDK (14+) that includes jpackage.");
        }
        if (getRuntimeImage().isPresent()
                && (!getAddModules().get().isEmpty() || !getJlinkOptions().get().isEmpty())) {
            throw new GradleException("jpackage.runtimeImage cannot be combined with addModules or"
                    + " jlinkOptions; a predefined runtime image is bundled as-is.");
        }

        File stagingDir = new File(getTemporaryDir(), "input");
        getFileSystemOperations().sync(spec -> {
            spec.from(getAppContent());
            spec.into(stagingDir);
        });

        String mainJar = getMainJarName().get();
        if (!new File(stagingDir, mainJar).isFile()) {
            throw new GradleException("Main jar '" + mainJar + "' is not among the packaged application files.");
        }

        File dest = getDestinationDir().get().getAsFile();
        getFileSystemOperations().delete(spec -> spec.delete(dest));

        List<String> command = new ArrayList<>();
        command.add(jpackage.getAbsolutePath());
        command.add("--type");
        command.add("app-image");
        command.add("--name");
        command.add(getAppName().get());
        command.add("--app-version");
        command.add(getAppVersion().get());
        command.add("--input");
        command.add(stagingDir.getAbsolutePath());
        command.add("--dest");
        command.add(dest.getAbsolutePath());
        command.add("--main-jar");
        command.add(mainJar);
        command.add("--main-class");
        command.add(getMainClass().get());
        if (getIcon().isPresent()) {
            command.add("--icon");
            command.add(getIcon().get().getAsFile().getAbsolutePath());
        }
        addIfPresent(command, "--vendor", getVendor());
        addIfPresent(command, "--description", getAppDescription());
        addIfPresent(command, "--copyright", getCopyright());
        for (String option : getJavaOptions().get()) {
            command.add("--java-options");
            command.add(option);
        }
        for (String argument : getAppArguments().get()) {
            command.add("--arguments");
            command.add(argument);
        }
        if (getRuntimeImage().isPresent()) {
            command.add("--runtime-image");
            command.add(getRuntimeImage().get().getAsFile().getAbsolutePath());
        } else {
            List<String> modules = getAddModules().get();
            if (!modules.isEmpty()) {
                command.add("--add-modules");
                command.add(String.join(",", modules));
            }
            List<String> jlinkOptions = getJlinkOptions().get();
            if (!jlinkOptions.isEmpty()) {
                command.add("--jlink-options");
                command.add(String.join(" ", jlinkOptions));
            }
        }
        if (getResourceDir().isPresent()) {
            command.add("--resource-dir");
            command.add(getResourceDir().get().getAsFile().getAbsolutePath());
        }
        if (OsUtil.isWindows() && getWinConsole().get()) {
            command.add("--win-console");
        }
        command.addAll(getExtraArgs().get());
        if (getVerbose().get()) {
            command.add("--verbose");
        }

        getLogger().info("Running: {}", String.join(" ", command));
        getExecOperations().exec(spec -> spec.commandLine(command));

        if (!getDocFiles().isEmpty()) {
            File appImageDir = getAppImageDirectory().get().getAsFile();
            getFileSystemOperations().copy(spec -> {
                spec.from(getDocFiles());
                spec.into(appImageDir);
            });
        }
    }

    private static void addIfPresent(List<String> command, String option, Property<String> value) {
        if (value.isPresent()) {
            command.add(option);
            command.add(value.get());
        }
    }
}
