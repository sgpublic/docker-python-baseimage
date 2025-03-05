package io.github.sgpublic.tasks

import io.github.sgpublic.BaseFlavor
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
import java.util.*

abstract class BaseImageDockerfile: DefaultTask() {
    @get:Input
    abstract val baseFlavor: Property<BaseFlavor>

    @InputDirectory
    fun getInput(): Provider<Directory> {
        return srcMainDir("/base/baseimage${baseFlavor.get().dockerfileSuffix}")
    }
    @OutputDirectory
    fun getOutput(): Provider<Directory> {
        return buildDir("./baseimage${baseFlavor.get().dockerfileSuffix}")
    }
    @get:OutputFile
    val destFile: Provider<RegularFile> get() = project.provider {
        getOutput().get().file("Dockerfile")
    }

    @TaskAction
    fun action() {
        val input = getInput().get().asFile
        val output = getOutput().get().asFile
        if (output.exists()) {
            project.delete(output)
        }
        if (!output.parentFile.exists()) {
            project.mkdir(output.parentFile)
        }
        for (source in input.walk()) {
            val dest = File(output, source.relativeTo(input).path)
            Files.copy(
                    Paths.get(source.toURI()),
                    Paths.get(dest.toURI()),
                    LinkOption.NOFOLLOW_LINKS,
                    StandardCopyOption.REPLACE_EXISTING,
            )
        }

        postProcessDockerfile()

        when (baseFlavor.get()) {
            BaseFlavor.COMMON -> postProcessCommon()
            BaseFlavor.GUI -> postProcessGui()
        }
    }

    private fun postProcessDockerfile() {
        val dockerFile = destFile.get().asFile
        val content = LinkedList(dockerFile.reader().readLines())
        var hasBuildPlatform = false
        for (line in content) {
            if (line == "ARG BUILDPLATFORM") {
                hasBuildPlatform = true
            }
        }
        if (!hasBuildPlatform) {
            content.addFirst("ARG BUILDPLATFORM")
        }
        dockerFile.writer().use { writer ->
            for (line in content) {
                writer.appendLine(line)
            }
        }
    }

    private fun postProcessCommon() {

    }

    private fun postProcessGui() {
        val buildShs = File(getOutput().get().asFile, "./src")
                .walk()
                .filter { it.isFile && it.name == "build.sh" }
        for (buildSh in buildShs) {
            val content = LinkedList(buildSh.readLines())
            buildSh.writer().use { writer ->
                for (line in content) {
                    if (line.startsWith("curl") && !line.contains("-k")) {
                        // to fix 'OpenSSL SSL_read: SSL_ERROR_SYSCALL, errno 0'
                        writer.appendLine(line.replace(
                                "curl -# -L -f",
                                "wget " +
                                        "--continue " +
                                        "--tries=3 " +
                                        "--waitretry=5 " +
                                        "-O -"
                        ))
                    } else if (line == "    curl \\") {
                        // install wget
                        writer.appendLine(line)
                        writer.appendLine("    wget \\")
                    } else {
                        writer.appendLine(line)
                    }
                }
            }
        }
    }

    override fun getGroup(): String {
        return "dockerfile"
    }
}