package io.github.danlewis783.jpackage;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JPackagePluginFunctionalTest {

    @TempDir
    Path projectDir;

    @BeforeEach
    void writeProject() throws IOException {
        write("settings.gradle", "rootProject.name = 'hello-cli'\n");
        write("build.gradle", String.join("\n",
                "plugins {",
                "    id 'java'",
                "    id 'io.github.danlewis783.jpackage'",
                "}",
                "version = '2.3.4'",
                // The packaged application targets Java 8 bytecode.
                "tasks.withType(JavaCompile).configureEach { options.release = 8 }",
                "jpackage {",
                "    appName = 'HelloCli'",
                "    mainClass = 'demo.Hello'",
                "    winConsole = true",
                "    addModules = ['java.base']",
                "    docFiles.from('README.txt', 'NOTES.txt')",
                "    devInstallDirectory = layout.buildDirectory.dir('dev-install')",
                "}",
                ""));
        write("README.txt", "readme\n");
        write("NOTES.txt", "notes\n");
        write("src/main/java/demo/Hello.java", String.join("\n",
                "package demo;",
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"hello from java \" + System.getProperty(\"java.version\"));",
                "    }",
                "}",
                ""));
    }

    @Test
    void buildsImageZipAndDevInstallThenIsUpToDate() throws Exception {
        BuildResult first = runner("jpackageZip", "jpackageInstall").build();
        assertEquals(TaskOutcome.SUCCESS, outcomeOf(first, ":jpackageImage"));
        assertEquals(TaskOutcome.SUCCESS, outcomeOf(first, ":jpackageZip"));
        assertEquals(TaskOutcome.SUCCESS, outcomeOf(first, ":jpackageInstall"));

        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

        // Doc files land in the image root.
        Path image = projectDir.resolve("build/jpackage/image/HelloCli");
        assertTrue(Files.isRegularFile(image.resolve("README.txt")), "README.txt in image root");
        assertTrue(Files.isRegularFile(image.resolve("NOTES.txt")), "NOTES.txt in image root");

        // Zip exists and contains a versioned top-level folder.
        Path zip = projectDir.resolve("build/distributions/HelloCli-2.3.4.zip");
        assertTrue(Files.isRegularFile(zip), "zip created");
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            assertNotNull(zipFile.getEntry("HelloCli-2.3.4/README.txt"), "doc file inside zip");
            String launcherEntry = windows
                    ? "HelloCli-2.3.4/HelloCli.exe"
                    : "HelloCli-2.3.4/bin/HelloCli";
            assertNotNull(zipFile.getEntry(launcherEntry), "launcher inside zip");
        }

        // Dev install is a copy of the image.
        Path devInstall = projectDir.resolve("build/dev-install");
        assertTrue(Files.isRegularFile(devInstall.resolve("README.txt")), "doc file in dev install");

        // The native launcher actually runs the Java 8-target app on the bundled runtime.
        Path launcher = windows ? image.resolve("HelloCli.exe") : image.resolve("bin/HelloCli");
        assertTrue(Files.isRegularFile(launcher), "launcher exists");
        String output = run(launcher);
        assertTrue(output.contains("hello from java"), "launcher output was: " + output);

        // Second build: nothing rebuilt, configuration cache reused.
        BuildResult second = runner("jpackageZip", "jpackageInstall").build();
        assertEquals(TaskOutcome.UP_TO_DATE, outcomeOf(second, ":jpackageImage"));
        assertEquals(TaskOutcome.UP_TO_DATE, outcomeOf(second, ":jpackageZip"));
        assertEquals(TaskOutcome.UP_TO_DATE, outcomeOf(second, ":jpackageInstall"));
        assertTrue(second.getOutput().contains("Reusing configuration cache")
                        || second.getOutput().contains("Configuration cache entry reused"),
                "configuration cache reused; output was:\n" + second.getOutput());
    }

    private GradleRunner runner(String... tasks) {
        List<String> args = new ArrayList<>(Arrays.asList(tasks));
        args.add("--configuration-cache");
        args.add("--stacktrace");
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(args);
    }

    private static TaskOutcome outcomeOf(BuildResult result, String taskPath) {
        assertNotNull(result.task(taskPath), taskPath + " was executed; output:\n" + result.getOutput());
        return result.task(taskPath).getOutcome();
    }

    private String run(Path launcher) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(launcher.toAbsolutePath().toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "launcher terminated");
        return output.toString();
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
