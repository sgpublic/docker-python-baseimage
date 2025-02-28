package io.github.sgpublic

import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import io.github.sgpublic.tasks.*
import io.github.sgpublic.utils.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.stdlib.capitalized
import java.io.File

enum class BaseFlavor(
       val tagSuffix: String,
       val taskSuffix: String,
       val dockerfileSuffix: String,
) {
    COMMON("", "", ""),
    GUI("-gui", "Gui", "-gui"),
}

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

        val baseImageInfo = File(target.rootDir, "versions/baseimage.json").reader().use {
            Gson.fromJson(it, BaseImageVersionInfo::class.java)
        }

        for (flavor in BaseFlavor.values()) {
//            val dockerCreateBaseDockerfile = target.tasks.register("base${flavor.taskSuffix}Dockerfile", BaseImageDockerfile::class.java) {
//                baseFlavor.set(flavor)
//            }
            for ((pyMinorVer, info) in target.PythonVersionsInfo().versions) {
                val simplyVersion = pyMinorVer.replace(".", "")
                for ((debianVer, pyFullVer) in info) {
                    val tagsBase = listOf(
                            "${DOCKER_TAG}:${pyMinorVer}-${debianVer}-base${flavor.tagSuffix}",
                            "${DOCKER_TAG}:${pyFullVer}-${debianVer}-base${flavor.tagSuffix}-${baseImageInfo.baseImage}",
                    )
                    val buildBase = target.tasks.register(
                            "build${flavor.taskSuffix}${simplyVersion}${debianVer.name.capitalized()}Image",
                            DockerBuildImage::class.java
                    ) {
                        group = "python${simplyVersion}"
                        inputDir.set(srcMainDir("./base/baseimage${flavor.dockerfileSuffix}"))
                        buildArgs.putAll(mapOf(
                                "BASEIMAGE" to when (flavor) {
                                    BaseFlavor.COMMON -> "python:$pyFullVer-slim-$debianVer"
                                    BaseFlavor.GUI -> "${DOCKER_TAG}:$pyFullVer-$debianVer-base${flavor.tagSuffix}"
                                },
                        ))
                        images.addAll(tagsBase)
                        platform.set("linux/amd64")
//                        dependsOn(dockerCreateBaseDockerfile)
                        dockerFile.set(srcMainFile("./base/baseimage${flavor.dockerfileSuffix}/Dockerfile"))
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsBase.last())
                    }

                    val tagsPoetry = listOf(
                            "${DOCKER_TAG}:${pyMinorVer}-${debianVer}${flavor.tagSuffix}",
                            "${DOCKER_TAG}:${pyFullVer}-${debianVer}${flavor.tagSuffix}",
                    )
                    val buildPoetry = target.tasks.register(
                            "build${flavor.taskSuffix}Poetry${simplyVersion}${debianVer.name.capitalized()}Image",
                            DockerBuildImage::class.java
                    ) {
                        group = "python${simplyVersion}"
                        inputDir.set(buildDir("poetry"))
                        buildArgs.putAll(mapOf(
                                "PYTHON_VERSION" to pyFullVer,
                                "DEBIAN_VERSION" to "$debianVer",
                                "BASE_FLAVOR" to flavor.tagSuffix,
                        ))
                        if (debianVer > DebianVersion.bullseye) {
                            buildArgs.put("__SOURCE_LIST_FILE", "/etc/apt/sources.list.d/debian.sources")
                        } else {
                            buildArgs.put("__SOURCE_LIST_FILE", "/etc/apt/sources.list")
                        }
                        images.addAll(tagsPoetry)
                        dockerFile.set(dockerCreatePoetryDockerfile.get().destFile)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPoetry.last())
                        mustRunAfter(buildBase)
                        dependsOn(buildBase, dockerCreatePoetryDockerfile)
                    }
                    val pushPoetry = target.tasks.register(
                            "push${flavor.taskSuffix}Poetry${simplyVersion}${debianVer.name.capitalized()}Image",
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
                            "build${flavor.taskSuffix}Playwright${simplyVersion}${debianVer.name.capitalized()}Image",
                            DockerBuildImage::class.java
                    ) {
                        group = "python${simplyVersion}"
                        mustRunAfter(buildPoetry)

                        buildArgs.putAll(mapOf(
                                "PYTHON_VERSION" to pyFullVer,
                                "DEBIAN_VERSION" to "$debianVer",
                                "BASE_FLAVOR" to flavor.tagSuffix,
                        ))
                        if (debianVer <= DebianVersion.bullseye) {
                            buildArgs.put("__BREAK_SYSTEM_PACKAGE", "")
                        } else {
                            buildArgs.put("__BREAK_SYSTEM_PACKAGE", "--break-system-package")
                        }
                        inputDir.set(buildDir("playwright"))
                        images.addAll(tagsPlaywright)
                        mustRunAfter(buildBase)
                        dependsOn(dockerCreatePlaywrightDockerfile)
                        dockerFile.set(dockerCreatePlaywrightDockerfile.get().destFile)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                    }
                    val pushPlaywright = target.tasks.register(
                            "push${flavor.taskSuffix}Playwright${simplyVersion}${debianVer.name.capitalized()}Image",
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
        }


        val dockerCreateCudaDockerfile = target.tasks.register("cudaDockerfile", CudaDockerfile::class.java) {
            destFile.set(buildFile("cuda/Dockerfile"))
        }
        val dockerCreateCudnnDockerfile = target.tasks.register("cudnnDockerfile", CudnnDockerfile::class.java) {
            destFile.set(buildFile("cudnn/Dockerfile"))
        }
        target.tasks.register("cudaVersions", CodaVersions::class.java)


        for (flavor in BaseFlavor.values()) {
            for ((cudaMinorVer, cudaInfo) in target.CudaVersionsInfo().versions) {
                val simplyCudaMinorVer = cudaMinorVer.replace(".", "")
                val debCudaMinorVer = cudaMinorVer.replace(".", "-")
                val cudaMajorVer = debCudaMinorVer.split("-")[0]
                for ((debianVer, cudaFullVer) in cudaInfo.debian) {
                    for ((pyMinorVer, pyInfo) in target.PythonVersionsInfo().versions) {
                        val simplePyMinorVer = pyMinorVer.replace(".", "")
                        val pyFullVer = pyInfo[debianVer] ?: continue
                        val cudaPoetryTags = listOf(
                                "${DOCKER_TAG}:$pyMinorVer-$debianVer${flavor.tagSuffix}-cuda$cudaMinorVer",
                                "${DOCKER_TAG}:${pyFullVer}-$debianVer${flavor.tagSuffix}-cuda$cudaFullVer",
                        )
                        val buildCudaPoetry = target.tasks.register(
                                "build${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
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
                                "push${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                DockerPushImage::class.java
                        ) {
                            group = "cuda${simplyCudaMinorVer}"
                            dependsOn(buildCudaPoetry)
                            images.addAll(cudaPoetryTags)
                            upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudaPoetryTags.last())
                        }

                        val tagsPlaywright = listOf(
                                "${DOCKER_TAG}:${pyMinorVer}-${debianVer}${flavor.tagSuffix}-playwright-cuda$cudaMinorVer",
                                "${DOCKER_TAG}:${pyFullVer}-${debianVer}${flavor.tagSuffix}-playwright-cuda$cudaFullVer",
                        )
                        val buildCudaPlaywright = target.tasks.register(
                                "build${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
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
                                "push${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
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
                                    "${DOCKER_TAG}:$pyMinorVer-$debianVer${flavor.tagSuffix}-cuda$cudaMinorVer-cudnn$cudnnMajorVer",
                                    "${DOCKER_TAG}:${pyFullVer}-$debianVer${flavor.tagSuffix}-cuda$cudaFullVer-cudnn$cudnnMajorVer",
                            )
                            val buildCudnnPoetry = target.tasks.register(
                                    "build${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
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
                                    "push${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
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
                                    "build${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
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
                                    "push${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
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
    }

    companion object {
        const val DOCKER_NAMESPACE = "mhmzx"
        const val DOCKER_REPOSITORY = "docker-python-baseimage"
        const val DOCKER_TAG = "$DOCKER_NAMESPACE/$DOCKER_REPOSITORY"
    }
}