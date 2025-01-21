pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    // Reuse version catalog from the main build.
    versionCatalogs {
        create("libs") { from(files("../gradle/poetry.versions.toml")) }
    }
}
