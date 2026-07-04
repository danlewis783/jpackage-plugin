package io.github.danlewis783.jpackage;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for packaging a Java application with the {@code jpackage} tool.
 *
 * <pre>
 * jpackage {
 *     appName = "MyApp"
 *     mainClass = "com.example.Main"
 *     icon = layout.projectDirectory.file("packaging/myapp.ico")
 *     docFiles.from("README.md", "LICENSE")
 *     installer {
 *         types = ["msi"]
 *     }
 * }
 * </pre>
 */
public abstract class JPackageExtension {

    private final InstallerOptions installer;

    @Inject
    public JPackageExtension(ObjectFactory objects) {
        this.installer = objects.newInstance(InstallerOptions.class);
    }

    /** Application name; also the launcher name. Defaults to the project name. */
    public abstract Property<String> getAppName();

    /**
     * Application version. Defaults to the project version, or {@code 1.0.0} when the project
     * version is unspecified. Windows installers require a numeric {@code x.y.z} version.
     */
    public abstract Property<String> getAppVersion();

    /** Fully qualified main class of the application. Required. */
    public abstract Property<String> getMainClass();

    /**
     * File name of the main jar within the packaged input. Defaults to the archive file name
     * of the {@code jar} task.
     */
    public abstract Property<String> getMainJar();

    /** Vendor shown in the app metadata and installers. */
    public abstract Property<String> getVendor();

    /** Description shown in the app metadata and installers. */
    public abstract Property<String> getDescription();

    /** Copyright string embedded in the application. */
    public abstract Property<String> getCopyright();

    /** Launcher icon ({@code .ico} on Windows, {@code .icns} on macOS, {@code .png} on Linux). */
    public abstract RegularFileProperty getIcon();

    /**
     * When {@code true} on Windows, the launcher opens a console window and connects stdio.
     * Use for command-line applications. Defaults to {@code false}.
     */
    public abstract Property<Boolean> getWinConsole();

    /** JVM options baked into the launcher ({@code --java-options}). */
    public abstract ListProperty<String> getJavaOptions();

    /** Default command-line arguments passed to the main class ({@code --arguments}). */
    public abstract ListProperty<String> getArguments();

    /**
     * Documentation files (README, LICENSE, manuals, ...) copied into the root of the
     * application image. They end up in the zip, the dev install, and the installers.
     */
    public abstract ConfigurableFileCollection getDocFiles();

    /**
     * Java major version of the JDK toolchain whose {@code jpackage} tool is used.
     * Defaults to the JVM running the build. This is independent of the Java version
     * your application targets.
     */
    public abstract Property<Integer> getJpackageJdkVersion();

    /**
     * A pre-built runtime image to bundle instead of one produced by jlink. Use this to ship
     * a Java 8 JRE with an application that must run on exactly that runtime. Cannot be
     * combined with {@link #getAddModules() addModules} or {@link #getJlinkOptions() jlinkOptions}.
     */
    public abstract DirectoryProperty getRuntimeImage();

    /**
     * Modules linked into the generated runtime image ({@code --add-modules}). When empty,
     * jpackage links its default module set, which is large; listing only what you need
     * (for example {@code ["java.desktop", "java.sql"]}) makes the image much smaller.
     */
    public abstract ListProperty<String> getAddModules();

    /** Options passed to jlink when the runtime image is generated ({@code --jlink-options}). */
    public abstract ListProperty<String> getJlinkOptions();

    /**
     * Directory of resources that override jpackage defaults ({@code --resource-dir}).
     * On Windows this is where {@code main.wxs}, {@code overrides.wxi}, WiX fragment
     * overrides, and {@code <AppName>-post-image.wsf} go — the hook for registry entries
     * and custom install-time actions.
     */
    public abstract DirectoryProperty getResourceDir();

    /** Extra arguments appended verbatim to the app-image jpackage invocation. */
    public abstract ListProperty<String> getExtraArgs();

    /** Passes {@code --verbose} to jpackage. Defaults to {@code false}. */
    public abstract Property<Boolean> getVerbose();

    /**
     * Local directory that {@code jpackageInstall} copies the application image into, for
     * developer self-use without an installer. Defaults to {@code <user home>/apps/<appName>}.
     * The task synchronizes the directory: files not part of the image are removed.
     */
    public abstract DirectoryProperty getDevInstallDirectory();

    /** Native installer options used by the {@code jpackageInstaller} task. */
    public InstallerOptions getInstaller() {
        return installer;
    }

    /** Configures the native installer options. */
    public void installer(Action<? super InstallerOptions> action) {
        action.execute(installer);
    }
}
