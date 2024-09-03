package io.github.sgpublic

import com.google.gson.Gson
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.platform.base.Platform
import java.io.File



typealias SinglePythonVersionInfo = Pair<String, PythonVersions.Platform>

data class PythonVersions(
    val versions: Map<String, VersionInfo>
) {
    data class VersionInfo(
        val verName: String,
        val platforms: Set<Platform>,
    )

    enum class Platform(
        val dir: String
    ) {
        bookworm("docker-bookworm"),
        bullseye("docker-bullseye"),
        ;
    }
}

fun Project.PythonVersions(): PythonVersions {
    return File(project.rootDir, "versions.json").reader().use {
        Gson().fromJson(it, PythonVersions::class.java)
    }
}
