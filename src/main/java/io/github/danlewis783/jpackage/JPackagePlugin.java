package io.github.danlewis783.jpackage;

import io.github.danlewis783.jpackage.internal.OsUtil;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Packages a Java application with the JDK's {@code jpackage} tool.
 *
 * <p>Applies the {@code java} plugin and adds:</p>
 * <ul>
 *   <li>{@code jpackageImage} — self-contained application image (launcher + bundled runtime)</li>
 *   <li>{@code jpackageZip} — the image packaged as a zip under {@code build/distributions}</li>
 *   <li>{@code jpackageInstall} — the image copied to a local directory for developer self-use</li>
 *   <li>{@code jpackageInstaller} — native installers (e.g. MSI) built from the image</li>
 * </ul>
 */
public abstract class JPackagePlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "jpackage";
    public static final String TASK_GROUP = "jpackage";
    public static final String IMAGE_TASK_NAME = "jpackageImage";
    public static final String ZIP_TASK_NAME = "jpackageZip";
    public static final String DEV_INSTALL_TASK_NAME = "jpackageInstall";
    public static final String INSTALLER_TASK_NAME = "jpackageInstaller";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        ProviderFactory providers = project.getProviders();
        ProjectLayout layout = project.getLayout();

        JPackageExtension extension = project.getExtensions().create(EXTENSION_NAME, JPackageExtension.class);
        InstallerOptions installer = extension.getInstaller();

        extension.getAppName().convention(project.getName());
        extension.getAppVersion().convention(providers.provider(() -> {
            String version = String.valueOf(project.getVersion());
            return Project.DEFAULT_VERSION.equals(version) ? "1.0.0" : version;
        }));
        extension.getWinConsole().convention(false);
        extension.getVerbose().convention(false);
        extension.getJpackageJdkVersion().convention(Integer.parseInt(JavaVersion.current().getMajorVersion()));
        extension.getDevInstallDirectory().convention(layout.dir(
                providers.systemProperty("user.home").zip(extension.getAppName(),
                        (home, name) -> new File(home, "apps" + File.separator + name))));

        installer.getSideBySide().convention(true);
        installer.getWinMenu().convention(true);
        installer.getWinShortcut().convention(true);
        installer.getWinMenuGroup().convention(extension.getAppName());
        installer.getTypes().convention(
                OsUtil.isWindows() ? Collections.singletonList("msi") : Collections.<String>emptyList());

        // Side-by-side installs: derive a per-version upgrade UUID (so MSI does not treat the new
        // version as an upgrade of the old one) and default the install dir to <name>/<version>.
        Provider<String> versionedUuid = extension.getAppName().zip(extension.getAppVersion(),
                (name, version) -> UUID.nameUUIDFromBytes(
                        (name + '/' + version).getBytes(StandardCharsets.UTF_8)).toString());
        Provider<String> versionedInstallDir = extension.getAppName().zip(extension.getAppVersion(),
                (name, version) -> name + '/' + version);
        Provider<String> absent = providers.provider(() -> (String) null);
        installer.getWinUpgradeUuid().convention(
                installer.getSideBySide().flatMap(sideBySide -> sideBySide ? versionedUuid : absent));
        installer.getInstallDir().convention(
                installer.getSideBySide().flatMap(sideBySide -> sideBySide ? versionedInstallDir : absent));

        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        Provider<JavaLauncher> jpackageLauncher = toolchains.launcherFor(spec ->
                spec.getLanguageVersion().set(extension.getJpackageJdkVersion().map(JavaLanguageVersion::of)));

        TaskProvider<Jar> jarTask = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class);
        extension.getMainJar().convention(jarTask.flatMap(Jar::getArchiveFileName));

        FileCollection runtimeClasspath =
                project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);

        TaskProvider<JPackageImageTask> imageTask =
                project.getTasks().register(IMAGE_TASK_NAME, JPackageImageTask.class, task -> {
                    task.setGroup(TASK_GROUP);
                    task.setDescription("Creates a self-contained application image with jpackage.");
                    task.getAppContent().from(jarTask, runtimeClasspath);
                    task.getAppName().set(extension.getAppName());
                    task.getAppVersion().set(extension.getAppVersion());
                    task.getMainClass().set(extension.getMainClass());
                    task.getMainJarName().set(extension.getMainJar());
                    task.getVendor().set(extension.getVendor());
                    task.getAppDescription().set(extension.getDescription());
                    task.getCopyright().set(extension.getCopyright());
                    task.getIcon().set(extension.getIcon());
                    task.getWinConsole().set(extension.getWinConsole());
                    task.getJavaOptions().set(extension.getJavaOptions());
                    task.getAppArguments().set(extension.getArguments());
                    task.getDocFiles().from(extension.getDocFiles());
                    task.getRuntimeImage().set(extension.getRuntimeImage());
                    task.getAddModules().set(extension.getAddModules());
                    task.getJlinkOptions().set(extension.getJlinkOptions());
                    task.getResourceDir().set(extension.getResourceDir());
                    task.getExtraArgs().set(extension.getExtraArgs());
                    task.getVerbose().set(extension.getVerbose());
                    task.getJavaLauncher().set(jpackageLauncher);
                    task.getDestinationDir().set(layout.getBuildDirectory().dir("jpackage/image"));
                });

        Provider<Directory> appImageDir = imageTask.flatMap(JPackageImageTask::getAppImageDirectory);
        Provider<String> baseName = extension.getAppName().zip(extension.getAppVersion(),
                (name, version) -> name + "-" + version);

        project.getTasks().register(ZIP_TASK_NAME, Zip.class, task -> {
            task.setGroup(TASK_GROUP);
            task.setDescription("Packages the jpackage application image as a zip.");
            task.getDestinationDirectory().set(layout.getBuildDirectory().dir("distributions"));
            task.getArchiveFileName().set(baseName.map(name -> name + ".zip"));
            task.from(appImageDir);
            task.into(baseName);
        });

        project.getTasks().register(DEV_INSTALL_TASK_NAME, Sync.class, task -> {
            task.setGroup(TASK_GROUP);
            task.setDescription("Copies the application image into jpackage.devInstallDirectory"
                    + " for local developer use.");
            task.from(appImageDir);
            task.into(extension.getDevInstallDirectory());
        });

        project.getTasks().register(INSTALLER_TASK_NAME, JPackageInstallerTask.class, task -> {
            task.setGroup(TASK_GROUP);
            task.setDescription("Builds native installers from the jpackage application image.");
            task.getAppImageDirectory().set(appImageDir);
            task.getTypes().set(installer.getTypes());
            task.getAppName().set(extension.getAppName());
            task.getAppVersion().set(extension.getAppVersion());
            task.getVendor().set(extension.getVendor());
            task.getLicenseFile().set(installer.getLicenseFile());
            task.getAboutUrl().set(installer.getAboutUrl());
            task.getInstallDir().set(installer.getInstallDir());
            task.getWinMenu().set(installer.getWinMenu());
            task.getWinMenuGroup().set(installer.getWinMenuGroup());
            task.getWinShortcut().set(installer.getWinShortcut());
            task.getWinShortcutPrompt().set(installer.getWinShortcutPrompt());
            task.getWinDirChooser().set(installer.getWinDirChooser());
            task.getWinPerUserInstall().set(installer.getWinPerUserInstall());
            task.getWinUpgradeUuid().set(installer.getWinUpgradeUuid());
            task.getResourceDir().set(extension.getResourceDir());
            task.getExtraArgs().set(installer.getExtraArgs());
            task.getVerbose().set(extension.getVerbose());
            task.getJavaLauncher().set(jpackageLauncher);
            task.getDestinationDir().set(layout.getBuildDirectory().dir("jpackage/installers"));
        });
    }
}
