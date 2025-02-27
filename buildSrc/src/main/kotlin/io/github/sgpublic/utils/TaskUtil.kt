package io.github.sgpublic.utils

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

fun Task.buildFile(name: String): Provider<RegularFile> {
    return project.layout.buildDirectory.file(name)
}

fun Task.buildDir(name: String): Provider<Directory> {
    return project.layout.buildDirectory.dir(name)
}

fun Project.findEnv(name: String) = provider {
    findProperty(name)?.toString()?.takeIf { it.isNotBlank() }
            ?: System.getenv(name.replace(".", "_").uppercase())
}

fun Task.findEnv(name: String) = project.findEnv(name)

val Task.CI_MODE: Boolean get() = findEnv("poetry.docker.ci.mode").getOrElse("0") == "1"

@Deprecated("由于版本不再区分，因此需要删除此语句", ReplaceWith(""))
fun AbstractDockerRemoteApiTask.upToDateWhenTagExist(namespace: String, repository: String, tag: String) {
//    outputs.upToDateWhen {
//        CI_MODE && project.dockerHubApi.tagExists(namespace, repository, tag)
//    }
}