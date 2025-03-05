package io.github.sgpublic.tasks

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import io.github.sgpublic.DockerPlugin
import io.github.sgpublic.utils.*
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class CudaDockerfile: Dockerfile() {
//    @InputDirectory
//    fun getInputDir(): java.io.File {
//        return project.file("./src/main/cuda")
//    }

    @OutputDirectory
    fun getOutputDir(): java.io.File {
        return buildDir("cuda").get().asFile
    }

    @TaskAction
    override fun create() {
        this.arg("PYTHON_VERSION")
        this.arg("DEBIAN_VERSION")
        this.arg("FLAVOR")
        this.from(From("${DockerPlugin.DOCKER_TAG}:\${PYTHON_VERSION}-\${DEBIAN_VERSION}\${FLAVOR}-${DockerPlugin.VERSION}"))

        this.arg("DEBIAN_VERSION_INT")
        runCommand(command(
                "curl -fsSL https://developer.download.nvidia.com/compute/cuda/repos/debian\${DEBIAN_VERSION_INT}/\$(arch)/cuda-keyring_1.1-1_all.deb -o /tmp/cuda-keyring.deb",
                aptInstall(
                        "/tmp/cuda-keyring.deb",
                ),
                rm(
                        "/tmp/cuda-keyring.deb",
                ),
                replaceNvidiaSourceListCommand(),
                "apt-get update",
        ))
        this.arg("CUDA_VERSION")
        this.environmentVariable("CUDA_VERSION", "\${CUDA_VERSION}")
        this.arg("CUDA_MINOR_VERSION")
        runCommand(command(
                aptInstall(
                        "cuda-toolkit-\${CUDA_MINOR_VERSION}"
                ),
        ))

        super.create()
    }

    override fun getGroup(): String {
        return "dockerfile"
    }
}