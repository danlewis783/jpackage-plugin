# jpackage-plugin

A Gradle plugin that packages Java applications with the JDK's
[`jpackage`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html) tool:

- **Application image** — a self-contained folder with a native launcher and a bundled Java
  runtime (no Java installation required on the target machine).
- **Zip** — the application image packaged as a zip for copy-and-run distribution.
- **Dev install** — the application image copied into a local directory for developer self-use.
- **Native installers** — MSI/EXE (Windows), with side-by-side versioning, launch icon,
  Start Menu entries, shortcuts, license page, and hooks for registry entries and
  install-time actions.

The plugin integrates at the `java` plugin level — it does **not** use or require the
`application` plugin. It is written in Java, requires **Gradle 9.6+**, and is fully
[configuration-cache](https://docs.gradle.org/current/userguide/configuration_cache.html)
compatible; all its tasks are incremental and build-cache friendly.

## Requirements

| What | Version |
|---|---|
| Gradle | 9.6+ (configuration cache supported and encouraged) |
| JDK running the build | 17+ (Gradle 9 requirement) |
| `jpackage` tool | JDK 14+; defaults to the JDK running the build, configurable via `jpackageJdkVersion` |
| MSI/EXE installers | [WiX Toolset](https://wixtoolset.org/) v3.0+ on the `PATH` (Windows only) |
| Packaged application | Any Java version — including Java 8 bytecode (see below) |

## Quick start

```kotlin
// settings.gradle.kts of a consumer — see "Consuming the plugin" below
plugins {
    java
    id("io.github.danlewis783.jpackage") version "0.1.0"
}

version = "1.2.3"

jpackage {
    appName.set("MyApp")
    mainClass.set("com.example.Main")          // required
    vendor.set("My Team")
    description.set("Does useful things")
    icon.set(layout.projectDirectory.file("packaging/myapp.ico"))
    docFiles.from("README.md", "LICENSE", "docs/manual.pdf")
    addModules.set(listOf("java.desktop"))     // keeps the bundled runtime small
    installer {
        types.set(listOf("msi"))
        licenseFile.set(layout.projectDirectory.file("LICENSE"))
    }
}
```

Then:

| Task | What it produces |
|---|---|
| `jpackageImage` | `build/jpackage/image/<appName>/` — launcher + runtime + doc files |
| `jpackageZip` | `build/distributions/<appName>-<version>.zip` (contents under a `<appName>-<version>/` folder) |
| `jpackageInstall` | Image copied to `devInstallDirectory` (default `<user home>/apps/<appName>`) |
| `jpackageInstaller` | `build/jpackage/installers/` — one installer per configured type |

The main jar and its entire `runtimeClasspath` are packaged automatically; the main jar
name defaults to the `jar` task's archive name. There is nothing to wire up manually.

## DSL reference

### `jpackage { ... }`

| Property | Default | Purpose |
|---|---|---|
| `appName` | project name | Application/launcher name |
| `appVersion` | project version | App version; Windows installers need numeric `x.y.z` |
| `mainClass` | — (required) | Fully qualified main class |
| `mainJar` | `jar` task archive name | Main jar file name |
| `vendor`, `description`, `copyright` | unset | App metadata |
| `icon` | unset | Launcher icon (`.ico` on Windows) |
| `winConsole` | `false` | Console launcher (stdio attached) for CLI apps |
| `javaOptions` | `[]` | JVM options baked into the launcher |
| `arguments` | `[]` | Default program arguments |
| `docFiles` | empty | Files copied into the image root (ship in zip, install, installer) |
| `jpackageJdkVersion` | JDK running the build | Toolchain major version providing the `jpackage` tool |
| `runtimeImage` | unset | Pre-built runtime to bundle as-is (e.g. a Java 8 JRE) |
| `addModules` | jpackage default | Modules jlinked into the generated runtime |
| `jlinkOptions` | jpackage default | Extra jlink options |
| `resourceDir` | unset | jpackage resource overrides (WiX customization — see below) |
| `extraArgs` | `[]` | Escape hatch: extra raw jpackage args (app-image step) |
| `verbose` | `false` | Pass `--verbose` to jpackage |
| `devInstallDirectory` | `<user home>/apps/<appName>` | Target of `jpackageInstall` (the task *synchronizes* this directory — it owns it) |

### `jpackage { installer { ... } }`

| Property | Default | Purpose |
|---|---|---|
| `types` | `["msi"]` on Windows | Installer types: `msi`, `exe`, `pkg`, `dmg`, `rpm`, `deb` |
| `sideBySide` | `true` | Different versions installable side by side (see below) |
| `installDir` | `<appName>/<version>` when side-by-side | Install location (relative under Program Files on Windows) |
| `licenseFile` | unset | License shown by the installer |
| `aboutUrl` | unset | URL in the uninstall entry |
| `winMenu` | `true` | Start Menu entry |
| `winMenuGroup` | `appName` | Start Menu folder |
| `winShortcut` | `true` | Desktop shortcut |
| `winShortcutPrompt` | unset | Let the user choose shortcuts during install |
| `winDirChooser` | unset | Let the user choose the install directory |
| `winPerUserInstall` | unset | Per-user instead of per-machine install |
| `winUpgradeUuid` | derived (see below) | Explicit MSI upgrade UUID |
| `extraArgs` | `[]` | Escape hatch: extra raw jpackage args (installer step) |

## Packaging Java 8 applications

Two supported approaches:

1. **Java 8 bytecode on a modern bundled runtime** (recommended). Compile with
   `options.release = 8`; the runtime that jpackage bundles (jlinked from a modern JDK)
   runs Java 8 bytecode natively. You keep modern JVM performance and security patches
   while the code stays Java 8 compatible.

2. **Ship an actual Java 8 JRE.** If the app depends on runtime behavior or APIs removed
   after Java 8, point `runtimeImage` at a JRE 8 directory:

   ```kotlin
   jpackage {
       runtimeImage.set(layout.projectDirectory.dir("C:/tools/jdk8u492-b09/jre"))
   }
   ```

   The runtime is bundled as-is (no jlink). `addModules`/`jlinkOptions` don't apply.

Either way, the `jpackage` *tool* itself always comes from a modern JDK
(`jpackageJdkVersion`, default: the JDK running the build).

## Side-by-side versions

`installer.sideBySide` is **on by default**: the MSI upgrade UUID is derived from the app
name *and version*, and the install directory defaults to `<appName>/<version>`. Windows
then treats 1.2.0 and 1.3.0 as unrelated products — installing the new version leaves the
old one in place, each with its own Add/Remove Programs entry.

Caveat: Start Menu entries and desktop shortcuts are named after `appName`, so the most
recently installed version wins those. If users need shortcuts per version, include the
version in `appName` (e.g. `appName.set("MyApp " + version)`).

Set `sideBySide.set(false)` to get classic upgrade behavior: jpackage's default upgrade
UUID is stable across versions, so installing a newer version replaces the older one.

## Registry entries and install-time actions

jpackage builds Windows installers from WiX sources, and every resource it uses can be
replaced by dropping a file of the same name into `resourceDir`
(passed as `--resource-dir`):

- **`main.wxs`** — the entire WiX project. Overriding it gives full MSI control: add
  `<RegistryValue>` elements, `<CustomAction>` elements that run a bundled utility,
  additional dialogs, etc.
- **`overrides.wxi`** — WiX variable overrides included by the default `main.wxs`.
- **`<AppName>-post-image.wsf`** — a Windows script executed after the application image
  is staged and *before* the MSI is compiled; useful for adding/patching files that must
  be part of the installed payload.
- Individual fragments such as `os-condition.wxf`, `ui.wxf` can likewise be overridden.

Workflow for customizing: run `jpackageInstaller` once with `verbose.set(true)` —
jpackage logs every resource it consults and the exact file name to place in the resource
directory (`--temp <dir>` via `installer.extraArgs` keeps the generated WiX sources around
to copy from). Start from the generated `main.wxs`, add your registry keys / custom
actions, and drop it into `resourceDir`.

References: [Overriding jpackage Resources (Oracle)](https://docs.oracle.com/en/java/javase/17/jpackage/override-jpackage-resources.html),
[jpackage man page](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html),
[Packaging Tool User's Guide](https://docs.oracle.com/en/java/javase/22/jpackage/packaging-tool-user-guide.pdf).

This replaces the common Advanced Installer use cases (registry tweaks, running a utility
during install) without any dependency beyond the JDK and WiX.

## Configuration cache and incremental builds

- All tasks declare precise inputs/outputs: unchanged sources ⇒ `UP-TO-DATE`, and
  `jpackageImage`/`jpackageInstaller` are `@CacheableTask` (relocatable via the build cache).
- The plugin performs no work at configuration time and is verified against
  `--configuration-cache` (the functional test asserts cache reuse).
- jpackage itself is only re-run when the jars, options, icon, docs, or the tool JDK change.

## Consuming the plugin

The plugin is published to the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.danlewis783.jpackage),
so on any machine it is just:

```kotlin
plugins {
    id("io.github.danlewis783.jpackage") version "0.1.0"
}
```

Inside a corporate firewall where the Portal is not directly reachable, point
`pluginManagement.repositories` at your internal mirror (Artifactory/Nexus proxies of the
Plugin Portal pick it up automatically).

Alternatives when the Portal is not an option:

1. **Composite build** (simplest; no repository needed):

   ```kotlin
   // settings.gradle.kts
   pluginManagement {
       includeBuild("../jpackage-plugin")   // path to a clone
   }
   ```

   then `id("io.github.danlewis783.jpackage")` in the plugins block (no version).

2. **Maven local**: `gradlew publishToMavenLocal` in the plugin repo, then in the consumer:

   ```kotlin
   // settings.gradle.kts
   pluginManagement {
       repositories {
           mavenLocal()
           gradlePluginPortal()
       }
   }
   ```

3. **Internal Maven repository**: add a `publishing { repositories { maven { ... } } }`
   block (credentials via environment/properties) and `gradlew publish`. The
   `java-gradle-plugin` + `maven-publish` combination already produces the plugin marker
   artifact, so consumers can use the plugin ID + version directly.

## Building this repository

```
gradlew build                       # compiles + runs the functional tests (runs jpackage)
gradlew -p samples/hello jpackageZip        # end-to-end sample (Java 8-target Swing app)
gradlew -p samples/hello jpackageInstaller  # sample MSI (requires WiX on PATH)
```

## License

[Apache License 2.0](LICENSE). Chosen because the plugin is published on GitHub for
internal corporate consumption: permissive (no copyleft obligations inside the firewall),
with an explicit patent grant that corporate legal teams generally accept without review.
