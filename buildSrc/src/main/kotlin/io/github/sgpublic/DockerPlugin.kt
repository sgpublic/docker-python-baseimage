package io.github.sgpublic

import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import io.github.sgpublic.tasks.*
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
                    group = "python${simplyVersion}"
                    inputDir.set(buildDir("poetry"))
                    buildArgs.putAll(mapOf(
                            "PYTHON_VERSION" to pyFullVer,
                            "DEBIAN_VERSION" to "$debianVer",
                    ))
                    if (debianVer > DebianVersion.bullseye) {
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
                    group = "python${simplyVersion}"
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
                    group = "python${simplyVersion}"
                    mustRunAfter(buildPoetry)

                    buildArgs.putAll(mapOf(
                            "PYTHON_VERSION" to pyFullVer,
                            "DEBIAN_VERSION" to "$debianVer",
                    ))
                    if (debianVer <= DebianVersion.bullseye) {
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
                    group = "python${simplyVersion}"
                    dependsOn(buildPlaywright)
                    images.addAll(tagsPlaywright)
                    upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                }

                if (debianVer <= DebianVersion.buster) {
                    // Buster 不受 PlayWright 官方支持
                    buildPlaywright.get().enabled = false
                    pushPlaywright.get().enabled = false
                }
                if (target.DOCKER_TOKEN == null) {
                    // 未设置 Token 时不允许 push
                    pushPoetry.get().enabled = false
                    pushPlaywright.get().enabled = false
                }
            }
        }


        val dockerCreateCudaDockerfile = target.tasks.register("cudaDockerfile", CudaDockerfile::class.java) {
            destFile.set(buildFile("cuda/Dockerfile"))
        }
        val dockerCreateCudnnDockerfile = target.tasks.register("cudnnDockerfile", CudnnDockerfile::class.java) {
            destFile.set(buildFile("cudnn/Dockerfile"))
        }
        target.tasks.register("cudaVersions", CodaVersions::class.java)

        for ((cudaMinorVer, cudaInfo) in target.CudaVersionsInfo().versions) {
            val simplyCudaMinorVer = cudaMinorVer.replace(".", "")
            val debCudaMinorVer = cudaMinorVer.replace(".", "-")
            val cudaMajorVer = debCudaMinorVer.split("-")[0]
            for ((debianVer, cudaFullVer) in cudaInfo.debian) {
                for ((pyMinorVer, pyInfo) in target.PythonVersionsInfo().versions) {
                    val simplePyMinorVer = pyMinorVer.replace(".", "")
                    val pyFullVer = pyInfo[debianVer] ?: continue
                    val cudaPoetryTags = listOf(
                            "${DOCKER_TAG}:$pyMinorVer-$debianVer-cuda$cudaMinorVer",
                            "${DOCKER_TAG}:${pyFullVer}-$debianVer-cuda$cudaFullVer",
                    )
                    val buildCudaPoetry = target.tasks.register(
                            "buildCuda${simplyCudaMinorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                            DockerBuildImage::class.java
                    ) {
                        group = "cuda${simplyCudaMinorVer}"
                        inputDir.set(buildDir("cuda"))
                        buildArgs.putAll(mapOf(
                                "PYTHON_VERSION" to pyFullVer,
                                "DEBIAN_VERSION" to "$debianVer",
                                "FLAVOR" to "",
                                "DEBIAN_VERSION_INT" to "${debianVer.numVer}",
                                "CUDA_VERSION" to cudaFullVer,
                                "CUDA_MINOR_VERSION" to debCudaMinorVer,
                        ))
                        images.addAll(cudaPoetryTags)
                        dependsOn(dockerCreateCudaDockerfile)
                        dockerFile.set(dockerCreateCudaDockerfile.get().destFile)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudaPoetryTags.last())
                    }
                    val pushCudaPoetry = target.tasks.register(
                            "pushCuda${simplyCudaMinorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                            DockerPushImage::class.java
                    ) {
                        group = "cuda${simplyCudaMinorVer}"
                        dependsOn(buildCudaPoetry)
                        images.addAll(cudaPoetryTags)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudaPoetryTags.last())
                    }

                    val tagsPlaywright = listOf(
                            "${DOCKER_TAG}:${pyMinorVer}-${debianVer}-playwright-cuda$cudaMinorVer",
                            "${DOCKER_TAG}:${pyFullVer}-${debianVer}-playwright-cuda$cudaFullVer",
                    )
                    val buildCudaPlaywright = target.tasks.register(
                            "buildCuda${simplyCudaMinorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                            DockerBuildImage::class.java
                    ) {
                        group = "cuda${simplyCudaMinorVer}"
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
                        group = "cuda${simplyCudaMinorVer}"
                        dependsOn(buildCudaPlaywright)
                        images.addAll(tagsPlaywright)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                    }

                    if (target.DOCKER_TOKEN == null) {
                        pushCudaPoetry.get().enabled = false
                        pushCudaPlaywright.get().enabled = false
                    }

                    for ((cudnnMajorVer, cudnnFullVer) in cudaInfo.cudnn) {
                        val cudnnPoetryTags = listOf(
                                "${DOCKER_TAG}:$pyMinorVer-$debianVer-cuda$cudaMinorVer-cudnn$cudnnMajorVer",
                                "${DOCKER_TAG}:${pyFullVer}-$debianVer-cuda$cudaFullVer-cudnn$cudnnMajorVer",
                        )
                        val buildCudnnPoetry = target.tasks.register(
                                "buildCuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                DockerBuildImage::class.java
                        ) {
                            group = "cudnn${cudnnMajorVer}-${simplyCudaMinorVer}"
                            inputDir.set(buildDir("cudnn"))
                            buildArgs.putAll(mapOf(
                                    "PYTHON_VERSION" to pyFullVer,
                                    "DEBIAN_VERSION" to "$debianVer",
                                    "CUDA_VERSION" to cudaFullVer,
                                    "FLAVOR" to "",
                                    "CUDNN_VERSION" to cudnnFullVer,
                                    "CUDNN_MAJOR_VERSION" to cudnnMajorVer,
                                    "CUDA_MAJOR_VERSION" to cudaMajorVer,
                            ))
                            images.addAll(cudnnPoetryTags)
                            mustRunAfter(buildCudaPoetry)
                            dependsOn(dockerCreateCudnnDockerfile)
                            dockerFile.set(dockerCreateCudnnDockerfile.get().destFile)
                            upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudnnPoetryTags.last())
                        }
                        val pushCudnnPoetry = target.tasks.register(
                                "pushCuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                DockerPushImage::class.java
                        ) {
                            group = "cudnn${cudnnMajorVer}-${simplyCudaMinorVer}"
                            dependsOn(buildCudnnPoetry)
                            images.addAll(cudnnPoetryTags)
                            upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudnnPoetryTags.last())
                        }

                        val cudnnPlaywrightTags = listOf(
                                "${DOCKER_TAG}:${pyMinorVer}-${debianVer}-playwright-cuda$cudaMinorVer",
                                "${DOCKER_TAG}:${pyFullVer}-${debianVer}-playwright-cuda$cudaFullVer",
                        )
                        val buildCudnnPlaywright = target.tasks.register(
                                "buildCuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                DockerBuildImage::class.java
                        ) {
                            group = "cudnn${cudnnMajorVer}-${simplyCudaMinorVer}"
                            buildArgs.putAll(mapOf(
                                    "PYTHON_VERSION" to pyFullVer,
                                    "DEBIAN_VERSION" to "$debianVer",
                                    "CUDA_VERSION" to cudaFullVer,
                                    "FLAVOR" to "-playwright",
                                    "CUDNN_VERSION" to cudnnFullVer,
                                    "CUDNN_MAJOR_VERSION" to cudnnMajorVer,
                                    "CUDA_MAJOR_VERSION" to cudaMajorVer,
                            ))
                            inputDir.set(buildDir("cudnn"))
                            images.addAll(cudnnPlaywrightTags)
                            mustRunAfter(buildCudaPlaywright)
                            dependsOn(dockerCreateCudnnDockerfile)
                            dockerFile.set(dockerCreateCudnnDockerfile.get().destFile)
                            upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudnnPoetryTags.last())
                        }
                        val pushCudnnPlaywright = target.tasks.register(
                                "pushCuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                DockerPushImage::class.java
                        ) {
                            group = "cuda${simplyCudaMinorVer}"
                            dependsOn(buildCudnnPlaywright)
                            images.addAll(cudnnPoetryTags)
                            upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudnnPoetryTags.last())
                        }

                        if (target.DOCKER_TOKEN == null) {
                            pushCudnnPoetry.get().enabled = false
                            pushCudnnPlaywright.get().enabled = false
                        }
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