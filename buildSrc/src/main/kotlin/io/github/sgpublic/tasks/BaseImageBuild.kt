package io.github.sgpublic.tasks

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import io.github.sgpublic.BaseFlavor
import io.github.sgpublic.DockerPlugin
import io.github.sgpublic.utils.DebianVersion
import io.github.sgpublic.utils.buildDir
import io.github.sgpublic.utils.srcMainDir
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.LinkedList

abstract class BaseImageBuild: DockerBuildImage() {
    @get:Input
    abstract val baseFlavor: Property<BaseFlavor>
    @get:Input
    abstract val pyVer: Property<String>
    @get:Input
    abstract val debVer: Property<DebianVersion>
    @get:Input
    abstract val baseImageVer: Property<String>
    @get:Input
    abstract val baseImageGuiVer: Property<String>
    @get:Input
    abstract val buildPlatform: Property<String>
    @get:Input
    abstract val targetPlatform: Property<String>
    @get:Input
    abstract val targetArch: Property<String>

    init {
        this.buildArgs.putAll(project.provider {
            val pyFullVer = pyVer.get()
            val debianVer = debVer.get()
            val flavor = baseFlavor.get()
            mapOf(
                    "BASEIMAGE" to when (flavor) {
                        BaseFlavor.COMMON -> "python:$pyFullVer-slim-$debianVer"
                        BaseFlavor.GUI -> "${DockerPlugin.DOCKER_TAG}:$pyFullVer-$debianVer-base-${DockerPlugin.VERSION}"
                    },
                    // to fix 'failed to parse platform : "" is an invalid component of ""'
                    "BUILDPLATFORM" to buildPlatform.get(),
                    "TARGETPLATFORM" to targetPlatform.get(),
                    "TARGETARCH" to targetArch.get(),
                    "IMAGE_VERSION" to when (flavor) {
                        BaseFlavor.COMMON -> baseImageVer.get()
                        BaseFlavor.GUI -> baseImageGuiVer.get()
                    },
            )
        })
    }
}