package io.github.sgpublic

import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import io.github.sgpublic.tasks.CudaDockerfile
import io.github.sgpublic.tasks.PlaywrightDockerfile
import io.github.sgpublic.tasks.PoetryDockerfile
import io.github.sgpublic.tasks.PythonVersions
import io.github.sgpublic.tasks.CodaVersions
import io.github.sgpublic.utils.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.stdlib.capitalized

class DockerPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(DockerRemoteApiPlugin::class.java)

        val dockerCreatePoetryDockerfile = target.tasks.register("poetryDockerfile", PoetryDockerfile::class.java) {
            destFile.set(buildFile("poetry/Dockerfile"))
        }
        val dockerCreatePlaywrightDockerfile = target.tasks.register("playwrightDockerfile", PlaywrightDockerfile::class.java) {
            destFile.set(buildFile("playwright/Dockerfile"))
        }
        target.tasks.register("pythonVersions", PythonVersions::class.java)

        for ((pyMinorVer, info) in target.PythonVersionsInfo().versions) {
            val simplyVersion = pyMinorVer.replace(".", "")
            for ((debianVer, pyFullVer) in info) {
                val tagsPoetry = listOf(
                        "${DOCKER_TAG}:$pyMinorVer-$debianVer",
                        "${DOCKER_TAG}:${pyFullVer}-$debianVer",
                )
                val buildPoetry = target.tasks.register(
                        "buildPoetry${simplyVersion}${debianVer.name.capitalized()}Image",
                        DockerBuildImage::class.java
                ) {
                    group = "python"
                    inputDir.set(buildDir("poetry"))
                    buildArgs.putAll(mapOf(
                            "PYTHON_VERSION" to pyFullVer,
                            "DEBIAN_VERSION" to "$debianVer",
                    ))
                    if (debianVer.numVer > DebianVersion.bullseye.numVer) {
                        buildArgs.put("__SOURCE_LIST_FILE", "/etc/apt/sources.list.d/debian.sources")
                    } else {
                        buildArgs.put("__SOURCE_LIST_FILE", "/etc/apt/sources.list")
                    }
                    images.addAll(tagsPoetry)
                    dockerFile.set(dockerCreatePoetryDockerfile.get().destFile)
                    upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPoetry.last())
                    dependsOn(dockerCreatePoetryDockerfile)
                }
                val pushPoetry = target.tasks.register(
                        "pushPoetry${simplyVersion}${debianVer.name.capitalized()}Image",
                        DockerPushImage::class.java
                ) {
                    group = "python"
                    dependsOn(buildPoetry)
                    images.addAll(tagsPoetry)
                    upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPoetry.last())
                }
    
                val tagsPlaywright = listOf(
                    "${DOCKER_TAG}:${pyMinorVer}-${debianVer}-playwright",
                    "${DOCKER_TAG}:${pyFullVer}-${debianVer}-playwright",
                )
                val buildPlaywright = target.tasks.register(
                        "buildPlaywright${simplyVersion}${debianVer.name.capitalized()}Image",
                        DockerBuildImage::class.java
                ) {
                    group = "python"
                    mustRunAfter(buildPoetry)

                    buildArgs.putAll(mapOf(
                            "PYTHON_VERSION" to pyFullVer,
                            "DEBIAN_VERSION" to "$debianVer",
                    ))
                    if (debianVer.numVer <= DebianVersion.bullseye.numVer) {
                        buildArgs.put("__BREAK_SYSTEM_PACKAGE", "")
                    } else {
                        buildArgs.put("__BREAK_SYSTEM_PACKAGE", "--break-system-package")
                    }
                    inputDir.set(buildDir("playwright"))
                    images.addAll(tagsPlaywright)
                    dependsOn(dockerCreatePlaywrightDockerfile)
                    dockerFile.set(dockerCreatePlaywrightDockerfile.get().destFile)
                    upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                }
                val pushPlaywright = target.tasks.register(
                        "pushPlaywright${simplyVersion}${debianVer.name.capitalized()}Image",
                        DockerPushImage::class.java
                ) {
                    group = "python"
                    dependsOn(buildPlaywright)
                    images.addAll(tagsPlaywright)
                    upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                }
    
                if (target.DOCKER_TOKEN == null) {
                    pushPoetry.get().enabled = false
                    pushPlaywright.get().enabled = false
                }
            }
        }


        val dockerCreateCudaDockerfile = target.tasks.register("cudaDockerfile", CudaDockerfile::class.java) {
            destFile.set(buildFile("cuda/Dockerfile"))
        }
        target.tasks.register("cudaVersions", CodaVersions::class.java)

        for ((cudaMinorVer, cudaInfo) in target.CudaVersionsInfo().versions) {
            val simplyCudaMinorVer = cudaMinorVer.replace(".", "")
            val debCudaMinorVer = cudaMinorVer.replace(".", "-")
            for ((debianVer, cudaFullVer) in cudaInfo) {
                for ((pyMinorVer, pyInfo) in target.PythonVersionsInfo().versions) {
                    val simplePyMinorVer = pyMinorVer.replace(".", "")
                    val pyFullVer = pyInfo[debianVer] ?: continue
                    val tagsPoetry = listOf(
                            "${DOCKER_TAG}:$pyMinorVer-$debianVer-cuda$cudaMinorVer",
                            "${DOCKER_TAG}:${pyFullVer}-$debianVer-cuda$cudaFullVer",
                    )
                    val buildCudaPoetry = target.tasks.register(
                            "buildCuda${simplyCudaMinorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                            DockerBuildImage::class.java
                    ) {
                        group = "cuda"
                        inputDir.set(buildDir("cuda"))
                        buildArgs.putAll(mapOf(
                                "PYTHON_VERSION" to pyFullVer,
                                "DEBIAN_VERSION" to "$debianVer",
                                "FLAVOR" to "",
                                "DEBIAN_VERSION_INT" to "${debianVer.numVer}",
                                "CUDA_VERSION" to cudaFullVer,
                                "CUDA_MINOR_VERSION" to debCudaMinorVer,
                        ))
                        images.addAll(tagsPoetry)
                        dependsOn(dockerCreateCudaDockerfile)
                        dockerFile.set(dockerCreateCudaDockerfile.get().destFile)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPoetry.last())
                    }
                    val pushCudaPoetry = target.tasks.register(
                            "pushCuda${simplyCudaMinorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                            DockerPushImage::class.java
                    ) {
                        group = "cuda"
                        dependsOn(buildCudaPoetry)
                        images.addAll(tagsPoetry)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPoetry.last())
                    }

                    val tagsPlaywright = listOf(
                            "${DOCKER_TAG}:${pyMinorVer}-${debianVer}-playwright-cuda$cudaMinorVer",
                            "${DOCKER_TAG}:${pyFullVer}-${debianVer}-playwright-cuda$cudaFullVer",
                    )
                    val buildCudaPlaywright = target.tasks.register(
                            "buildCuda${simplyCudaMinorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                            DockerBuildImage::class.java
                    ) {
                        group = "cuda"
                        buildArgs.putAll(mapOf(
                                "PYTHON_VERSION" to pyFullVer,
                                "DEBIAN_VERSION" to "$debianVer",
                                "FLAVOR" to "-playwright",
                                "DEBIAN_VERSION_INT" to "${debianVer.numVer}",
                                "CUDA_VERSION" to cudaFullVer,
                                "CUDA_MINOR_VERSION" to debCudaMinorVer,
                        ))
                        inputDir.set(buildDir("cuda"))
                        images.addAll(tagsPlaywright)
                        dependsOn(dockerCreateCudaDockerfile)
                        dockerFile.set(dockerCreateCudaDockerfile.get().destFile)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                    }
                    val pushCudaPlaywright = target.tasks.register(
                            "pushCuda${simplyCudaMinorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                            DockerPushImage::class.java
                    ) {
                        group = "cuda"
                        dependsOn(buildCudaPlaywright)
                        images.addAll(tagsPlaywright)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                    }

                    if (target.DOCKER_TOKEN == null) {
                        pushCudaPoetry.get().enabled = false
                        pushCudaPlaywright.get().enabled = false
                    }
                }
            }
        }
    }

    companion object {
        const val DOCKER_NAMESPACE = "mhmzx"
        const val DOCKER_REPOSITORY = "poetry-runner"
        const val DOCKER_TAG = "$DOCKER_NAMESPACE/$DOCKER_REPOSITORY"
    }
}