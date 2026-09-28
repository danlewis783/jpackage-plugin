package io.github.danlewis783.jpackage;

import io.github.danlewis783.jpackage.internal.OsUtil;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Covers the installer conventions, validation, and command assembly without running jpackage. */
class JPackageInstallerTaskTest {

    @TempDir
    Path projectDir;

    private Project project;
    private JPackageExtension extension;

    @BeforeEach
    void applyPlugin() {
        project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        project.setVersion("1.2.3");
        project.getPluginManager().apply(JPackagePlugin.class);
        extension = project.getExtensions().getByType(JPackageExtension.class);
        extension.getAppName().set("App");
        extension.getMainClass().set("demo.Main");
        extension.getDescription().set("Does useful things");
    }

    @Test
    void sideBySideDerivesVersionedUpgradeUuidAndInstallDirOnWindows() {
        List<String> command = command("msi");

        assertEquals("Does useful things", valueOf(command, "--description"));
        if (OsUtil.isWindows()) {
            String expectedUuid = UUID.nameUUIDFromBytes("App/1.2.3".getBytes(StandardCharsets.UTF_8)).toString();
            assertEquals(expectedUuid, valueOf(command, "--win-upgrade-uuid"));
            assertEquals("App/1.2.3", valueOf(command, "--install-dir"));
            assertTrue(command.contains("--win-menu"));
            assertEquals("App", valueOf(command, "--win-menu-group"));
            assertTrue(command.contains("--win-shortcut"));
        } else {
            // A relative install dir is invalid on Linux/macOS, and --win-* options do not apply.
            assertFalse(command.contains("--install-dir"));
            assertFalse(command.stream().anyMatch(arg -> arg.startsWith("--win-")));
        }
    }

    @Test
    void withoutSideBySideUsesJpackageDefaults() {
        extension.getInstaller().getSideBySide().set(false);
        List<String> command = command("msi");

        assertFalse(command.contains("--win-upgrade-uuid"));
        assertFalse(command.contains("--install-dir"));
    }

    @Test
    void rejectsInstallerTypesOfOtherPlatforms() {
        String foreignType = OsUtil.isWindows() ? "dmg" : "msi";
        extension.getInstaller().getTypes().set(Collections.singletonList(foreignType));

        GradleException error = assertThrows(GradleException.class, () -> installerTask().validate());
        assertTrue(error.getMessage().contains(foreignType), error.getMessage());
    }

    @Test
    void enforcesMsiVersionRulesOnWindows() {
        assumeTrue(OsUtil.isWindows());
        for (String valid : new String[] {"1.2", "1.2.3", "1.2.3.4", "255.255.65535"}) {
            extension.getAppVersion().set(valid);
            assertDoesNotThrow(() -> installerTask().validate(), valid);
        }
        for (String invalid : new String[] {"1", "1.2.3.4.5", "256.0.0", "1.256.0", "1.2.65536", "1.0-SNAPSHOT"}) {
            extension.getAppVersion().set(invalid);
            assertThrows(GradleException.class, () -> installerTask().validate(), invalid);
        }
    }

    @Test
    void imageTaskRejectsNonNumericVersionOnWindows() {
        assumeTrue(OsUtil.isWindows());
        extension.getAppVersion().set("1.0-SNAPSHOT");
        JPackageImageTask imageTask = project.getTasks()
                .named(JPackagePlugin.IMAGE_TASK_NAME, JPackageImageTask.class).get();

        assertThrows(GradleException.class, imageTask::checkNumericVersionOnWindows);
    }

    private JPackageInstallerTask installerTask() {
        return project.getTasks().named(JPackagePlugin.INSTALLER_TASK_NAME, JPackageInstallerTask.class).get();
    }

    private List<String> command(String type) {
        return installerTask().buildCommand(new File("jpackage"), type);
    }

    private static String valueOf(List<String> command, String option) {
        int index = command.indexOf(option);
        assertTrue(index >= 0 && index + 1 < command.size(), option + " in " + command);
        return command.get(index + 1);
    }
}
