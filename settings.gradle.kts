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
        mavenCentral()
        google()
    }

    // https://docs.gradle.org/current/userguide/platforms.html#sec:importing-catalog-from-file
    versionCatalogs {
        val poetry by creating {
            from(files(File(rootDir, "./gradle/poetry.versions.toml")))
        }
    }
}

rootProject.name = "poetry-docker"
