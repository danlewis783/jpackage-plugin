package io.github.danlewis783.jpackage;

import io.github.danlewis783.jpackage.internal.OsUtil;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs {@code jpackage --type app-image} to produce a self-contained application image
 * (native launcher plus bundled Java runtime), then copies the configured documentation
 * files into the image root ({@code Contents/Resources} of the bundle on macOS).
 */
@CacheableTask
public abstract class JPackageImageTask extends AbstractJPackageTask {

    /** Jars packaged as the application: the main jar and its runtime classpath. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getAppContent();

    @Input
    public abstract Property<String> getMainClass();

    /** File name of the main jar within {@link #getAppContent()}. */
    @Input
    public abstract Property<String> getMainJarName();

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

    @Input
    public abstract ListProperty<String> getAddModules();

    @Input
    public abstract ListProperty<String> getJlinkOptions();

    /** The application image directory produced by this task; the destination dir holds only it. */
    @Internal
    public Provider<Directory> getAppImageDirectory() {
        return getDestinationDir().dir(getAppName().map(name -> OsUtil.isMacOs() ? name + ".app" : name));
    }

    @TaskAction
    public void createAppImage() {
        File jpackage = resolveJpackage();
        checkNumericVersionOnWindows();
        if (getRuntimeImage().isPresent()
                && (!getAddModules().get().isEmpty() || !getJlinkOptions().get().isEmpty())) {
            throw new GradleException("jpackage.runtimeImage cannot be combined with addModules or"
                    + " jlinkOptions; a predefined runtime image is bundled as-is.");
        }
        checkUniqueFileNames();

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

        List<String> command = newCommand(jpackage, "app-image");
        command.add("--input");
        command.add(stagingDir.getAbsolutePath());
        command.add("--main-jar");
        command.add(mainJar);
        command.add("--main-class");
        command.add(getMainClass().get());
        if (getIcon().isPresent()) {
            command.add("--icon");
            command.add(getIcon().get().getAsFile().getAbsolutePath());
        }
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
        if (OsUtil.isWindows() && getWinConsole().get()) {
            command.add("--win-console");
        }
        runJpackage(command);

        if (!getDocFiles().isEmpty()) {
            // Files at the root of a macOS .app bundle make it invalid and break code signing.
            File appImageDir = getAppImageDirectory().get().getAsFile();
            File docDir = OsUtil.isMacOs() ? new File(appImageDir, "Contents/Resources") : appImageDir;
            getFileSystemOperations().copy(spec -> {
                spec.from(getDocFiles());
                spec.into(docDir);
            });
        }
    }

    /** jpackage takes a flat input directory, so two jars with the same file name would collide. */
    private void checkUniqueFileNames() {
        Map<String, List<File>> byName = new TreeMap<>();
        for (File file : getAppContent().getFiles()) {
            byName.computeIfAbsent(file.getName(), name -> new ArrayList<>()).add(file);
        }
        StringBuilder clashes = new StringBuilder();
        byName.forEach((name, files) -> {
            if (files.size() > 1) {
                clashes.append("\n  ").append(name).append(": ").append(files);
            }
        });
        if (clashes.length() > 0) {
            throw new GradleException("Packaged application files must have unique names, but these"
                    + " runtime classpath entries clash:" + clashes);
        }
    }
}
