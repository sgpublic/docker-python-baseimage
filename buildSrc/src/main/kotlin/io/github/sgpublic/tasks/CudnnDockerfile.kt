package io.github.sgpublic.tasks

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import io.github.sgpublic.DockerPlugin
import io.github.sgpublic.utils.*
import org.gradle.api.tasks.*

abstract class CudnnDockerfile: Dockerfile() {
//    @InputDirectory
//    fun getInputDir(): java.io.File {
//        return project.file("./src/main/cudnn")
//    }

    @OutputDirectory
    fun getOutputDir(): java.io.File {
        return buildDir("cudnn").get().asFile
    }

    @TaskAction
    override fun create() {
        this.arg("PYTHON_VERSION")
        this.arg("DEBIAN_VERSION")
        this.arg("CUDA_VERSION")
        this.arg("FLAVOR")
        this.from(From("${DockerPlugin.DOCKER_TAG}:\${PYTHON_VERSION}-\${DEBIAN_VERSION}\${FLAVOR}-cuda\${CUDA_VERSION}-${DockerPlugin.VERSION}"))

        this.arg("CUDNN_VERSION")
        this.environmentVariable("CUDNN_VERSION", "\${CUDNN_VERSION}")
        this.arg("CUDNN_MAJOR_VERSION")
        this.arg("CUDA_MAJOR_VERSION")
        runCommand(command(
                aptInstall(
                        "cudnn\${CUDNN_MAJOR_VERSION}-cuda-\${CUDA_MAJOR_VERSION}"
                ),
        ))

        super.create()
    }

    override fun getGroup(): String {
        return "dockerfile"
    }
}