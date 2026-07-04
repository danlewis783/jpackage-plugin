package io.github.danlewis783.jpackage;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Options for building native installers from the application image.
 */
public abstract class InstallerOptions {

    /**
     * Installer types to produce, e.g. {@code ["msi"]} or {@code ["msi", "exe"]}.
     * Defaults to {@code ["msi"]} on Windows and the platform default elsewhere.
     * Building {@code msi}/{@code exe} requires the WiX Toolset on the PATH.
     */
    public abstract ListProperty<String> getTypes();

    /** License file displayed by the installer ({@code --license-file}). */
    public abstract RegularFileProperty getLicenseFile();

    /** URL shown in the installer/uninstall entry ({@code --about-url}). */
    public abstract Property<String> getAboutUrl();

    /**
     * Installation directory. On Windows this is a relative sub-path below the default
     * installation location, e.g. {@code "MyApp/1.2.3"}. When {@link #getSideBySide() sideBySide}
     * is enabled (the default) this defaults to {@code <appName>/<appVersion>}.
     */
    public abstract Property<String> getInstallDir();

    /**
     * When {@code true} (the default), different versions of the application can be installed
     * side by side: the Windows upgrade UUID is derived from the app name <em>and version</em>,
     * and the install directory defaults to {@code <appName>/<appVersion>}. Installing a new
     * version then does not remove the previous one.
     *
     * <p>When {@code false}, jpackage's default upgrade UUID (stable across versions) is used,
     * so installing a newer version upgrades/replaces the older one.</p>
     */
    public abstract Property<Boolean> getSideBySide();

    /** Adds a Start Menu entry ({@code --win-menu}). Defaults to {@code true}. */
    public abstract Property<Boolean> getWinMenu();

    /** Start Menu group (folder) name ({@code --win-menu-group}). Defaults to the app name. */
    public abstract Property<String> getWinMenuGroup();

    /** Adds a desktop shortcut ({@code --win-shortcut}). Defaults to {@code true}. */
    public abstract Property<Boolean> getWinShortcut();

    /** Lets the user choose shortcuts during install ({@code --win-shortcut-prompt}). */
    public abstract Property<Boolean> getWinShortcutPrompt();

    /** Lets the user choose the install directory ({@code --win-dir-chooser}). */
    public abstract Property<Boolean> getWinDirChooser();

    /** Installs per-user instead of per-machine ({@code --win-per-user-install}). */
    public abstract Property<Boolean> getWinPerUserInstall();

    /**
     * Explicit MSI upgrade UUID ({@code --win-upgrade-uuid}). Setting this overrides the
     * {@link #getSideBySide() sideBySide} derivation.
     */
    public abstract Property<String> getWinUpgradeUuid();

    /** Extra arguments appended verbatim to each installer jpackage invocation. */
    public abstract ListProperty<String> getExtraArgs();
}
