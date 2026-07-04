plugins {
    java
    id("io.github.danlewis783.jpackage")
}

version = "1.2.3"

// The application targets Java 8 bytecode; the bundled runtime (jlinked from a modern JDK)
// runs it fine. To ship an actual Java 8 JRE instead, see jpackage.runtimeImage.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

jpackage {
    appName.set("HelloJPackage")
    mainClass.set("hello.Hello")
    vendor.set("Dan Lewis")
    description.set("Sample Swing application packaged by the jpackage plugin")
    icon.set(layout.projectDirectory.file("packaging/hello.ico"))
    docFiles.from("README.txt")
    // Keep the bundled runtime small: only the modules the app needs.
    addModules.set(listOf("java.desktop"))
    installer {
        types.set(listOf("msi")) // requires the WiX Toolset; sideBySide is on by default
    }
}
