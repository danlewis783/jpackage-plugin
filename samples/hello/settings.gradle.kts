pluginManagement {
    // Resolve the jpackage plugin from the enclosing repository. A real consumer would
    // instead resolve it from an internal Maven repository or use includeBuild on a clone.
    includeBuild("../..")
}

rootProject.name = "hello"
