package io.github.sgpublic

import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import io.github.sgpublic.tasks.*
import io.github.sgpublic.utils.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
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
    override fun apply(project: Project) {
        version = project.findEnv("project.version")
        
        project.pluginManager.apply(DockerRemoteApiPlugin::class.java)

        val baseImageInfo = File(project.rootDir, "versions/baseimage.json").reader().use {
            Gson.fromJson(it, BaseImageVersionInfo::class.java)
        }

        val dockerCreatePoetryDockerfile = project.tasks.create("poetryDockerfile", PoetryDockerfile::class.java) {
            destFile.set(buildFile("poetry/Dockerfile"))
        }
        val dockerCreatePlaywrightDockerfile = project.tasks.create("playwrightDockerfile", PlaywrightDockerfile::class.java) {
            destFile.set(buildFile("playwright/Dockerfile"))
        }
        project.tasks.create("pythonVersions", PythonVersions::class.java)


        for (flavor in BaseFlavor.values()) {
            val dockerCreateBaseDockerfile = project.tasks.create("base${flavor.taskSuffix}Dockerfile", BaseImageDockerfile::class.java) {
                baseFlavor.set(flavor)
            }
            for ((pyMinorVer, info) in project.PythonVersionsInfo().versions) {
                val simplyVersion = pyMinorVer.replace(".", "")
                for ((debianVer, pyFullVer) in info) {
                    val tagsBase = listOf(
                            "${DOCKER_TAG}:${pyMinorVer}-${debianVer}-base${flavor.tagSuffix}",
                            "${DOCKER_TAG}:${pyFullVer}-${debianVer}-base${flavor.tagSuffix}",
                            "${DOCKER_TAG}:${pyFullVer}-${debianVer}-base${flavor.tagSuffix}-$VERSION",
                    )
                    val buildBase = project.tasks.create(
                            "build${flavor.taskSuffix}${simplyVersion}${debianVer.name.capitalized()}Image",
                            BaseImageBuild::class.java
                    ) {
                        group = "python${simplyVersion}"
                        inputDir.set(dockerCreateBaseDockerfile.getOutput())
                        pyVer.set(pyFullVer)
                        baseFlavor.set(flavor)
                        debVer.set(debianVer)
                        images.addAll(tagsBase)
                        baseImageVer.set(baseImageInfo.baseImage)
                        baseImageGuiVer.set(baseImageInfo.baseImageGui)
                        buildPlatform.set("linux/amd64")
                        targetPlatform.set("linux/amd64")
                        targetArch.set("x86_64")
                        dependsOn(dockerCreateBaseDockerfile)
                        dockerFile.set(dockerCreateBaseDockerfile.destFile)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsBase.last())
                    }

                    val tagsPoetry = listOf(
                            "${DOCKER_TAG}:${pyMinorVer}-${debianVer}${flavor.tagSuffix}",
                            "${DOCKER_TAG}:${pyFullVer}-${debianVer}${flavor.tagSuffix}-$VERSION",
                            "${DOCKER_TAG}:${pyFullVer}-${debianVer}${flavor.tagSuffix}",
                    )
                    val buildPoetry = project.tasks.create(
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
                        dockerFile.set(dockerCreatePoetryDockerfile.destFile)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPoetry.last())
                        mustRunAfter(buildBase)
                        dependsOn(buildBase, dockerCreatePoetryDockerfile)
                    }
                    val pushPoetry = project.tasks.create(
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
                            "${DOCKER_TAG}:${pyFullVer}-${debianVer}-playwright-$VERSION",
                    )
                    val buildPlaywright = project.tasks.create(
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
                        dockerFile.set(dockerCreatePlaywrightDockerfile.destFile)
                        upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                    }
                    val pushPlaywright = project.tasks.create(
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
                        buildPlaywright.enabled = false
                        pushPlaywright.enabled = false
                    }
                    if (project.DOCKER_TOKEN == null) {
                        // 未设置 Token 时不允许 push
                        pushPoetry.enabled = false
                        pushPlaywright.enabled = false
                    }
                }
            }
        }


        val dockerCreateCudaDockerfile = project.tasks.create("cudaDockerfile", CudaDockerfile::class.java) {
            destFile.set(buildFile("cuda/Dockerfile"))
        }
        val dockerCreateCudnnDockerfile = project.tasks.create("cudnnDockerfile", CudnnDockerfile::class.java) {
            destFile.set(buildFile("cudnn/Dockerfile"))
        }
        project.tasks.create("cudaVersions", CodaVersions::class.java)


        for (flavor in BaseFlavor.values()) {
            for ((cudaMinorVer, cudaInfo) in project.CudaVersionsInfo().versions) {
                val simplyCudaMinorVer = cudaMinorVer.replace(".", "")
                val debCudaMinorVer = cudaMinorVer.replace(".", "-")
                val cudaMajorVer = debCudaMinorVer.split("-")[0]
                for ((debianVer, cudaFullVer) in cudaInfo.debian) {
                    for ((pyMinorVer, pyInfo) in project.PythonVersionsInfo().versions) {
                        val simplePyMinorVer = pyMinorVer.replace(".", "")
                        val pyFullVer = pyInfo[debianVer] ?: continue
                        val cudaPoetryTags = listOf(
                                "${DOCKER_TAG}:$pyMinorVer-$debianVer${flavor.tagSuffix}-cuda$cudaMinorVer",
                                "${DOCKER_TAG}:${pyFullVer}-$debianVer${flavor.tagSuffix}-cuda$cudaFullVer",
                                "${DOCKER_TAG}:${pyFullVer}-$debianVer${flavor.tagSuffix}-cuda$cudaFullVer-$VERSION",
                        )
                        val buildCudaPoetry = project.tasks.create(
                                "build${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                DockerBuildImage::class.java
                        ) {
                            group = "cuda${simplyCudaMinorVer}"
                            inputDir.set(buildDir("cuda"))
                            buildArgs.putAll(mapOf(
                                    "PYTHON_VERSION" to pyFullVer,
                                    "DEBIAN_VERSION" to "$debianVer",
                                    "FLAVOR" to flavor.tagSuffix,
                                    "DEBIAN_VERSION_INT" to "${debianVer.numVer}",
                                    "CUDA_VERSION" to cudaFullVer,
                                    "CUDA_MINOR_VERSION" to debCudaMinorVer,
                            ))
                            images.addAll(cudaPoetryTags)
                            dependsOn(dockerCreateCudaDockerfile)
                            dockerFile.set(dockerCreateCudaDockerfile.destFile)
                            upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudaPoetryTags.last())
                        }
                        val pushCudaPoetry = project.tasks.create(
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
                                "${DOCKER_TAG}:${pyFullVer}-${debianVer}${flavor.tagSuffix}-playwright-cuda$cudaFullVer-$VERSION",
                        )
                        val buildCudaPlaywright = project.tasks.create(
                                "build${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                DockerBuildImage::class.java
                        ) {
                            group = "cuda${simplyCudaMinorVer}"
                            buildArgs.putAll(mapOf(
                                    "PYTHON_VERSION" to pyFullVer,
                                    "DEBIAN_VERSION" to "$debianVer",
                                    "FLAVOR" to "-playwright${flavor.tagSuffix}",
                                    "DEBIAN_VERSION_INT" to "${debianVer.numVer}",
                                    "CUDA_VERSION" to cudaFullVer,
                                    "CUDA_MINOR_VERSION" to debCudaMinorVer,
                            ))
                            inputDir.set(buildDir("cuda"))
                            images.addAll(tagsPlaywright)
                            dependsOn(dockerCreateCudaDockerfile)
                            dockerFile.set(dockerCreateCudaDockerfile.destFile)
                            upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                        }
                        val pushCudaPlaywright = project.tasks.create(
                                "push${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                DockerPushImage::class.java
                        ) {
                            group = "cuda${simplyCudaMinorVer}"
                            dependsOn(buildCudaPlaywright)
                            images.addAll(tagsPlaywright)
                            upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, tagsPlaywright.last())
                        }

                        if (project.DOCKER_TOKEN == null) {
                            pushCudaPoetry.enabled = false
                            pushCudaPlaywright.enabled = false
                        }

                        for ((cudnnMajorVer, cudnnFullVer) in cudaInfo.cudnn) {
                            val cudnnPoetryTags = listOf(
                                    "${DOCKER_TAG}:$pyMinorVer-$debianVer${flavor.tagSuffix}-cuda$cudaMinorVer-cudnn$cudnnMajorVer",
                                    "${DOCKER_TAG}:${pyFullVer}-$debianVer${flavor.tagSuffix}-cuda$cudaFullVer-cudnn$cudnnMajorVer",
                                    "${DOCKER_TAG}:${pyFullVer}-$debianVer${flavor.tagSuffix}-cuda$cudaFullVer-cudnn$cudnnMajorVer-$VERSION",
                            )
                            val buildCudnnPoetry = project.tasks.create(
                                    "build${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Poetry${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                    DockerBuildImage::class.java
                            ) {
                                group = "cudnn${cudnnMajorVer}-${simplyCudaMinorVer}"
                                inputDir.set(buildDir("cudnn"))
                                buildArgs.putAll(mapOf(
                                        "PYTHON_VERSION" to pyFullVer,
                                        "DEBIAN_VERSION" to "$debianVer",
                                        "CUDA_VERSION" to cudaFullVer,
                                        "FLAVOR" to flavor.tagSuffix,
                                        "CUDNN_VERSION" to cudnnFullVer,
                                        "CUDNN_MAJOR_VERSION" to cudnnMajorVer,
                                        "CUDA_MAJOR_VERSION" to cudaMajorVer,
                                ))
                                images.addAll(cudnnPoetryTags)
                                mustRunAfter(buildCudaPoetry)
                                dependsOn(dockerCreateCudnnDockerfile)
                                dockerFile.set(dockerCreateCudnnDockerfile.destFile)
                                upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudnnPoetryTags.last())
                            }
                            val pushCudnnPoetry = project.tasks.create(
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
                            val buildCudnnPlaywright = project.tasks.create(
                                    "build${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                    DockerBuildImage::class.java
                            ) {
                                group = "cudnn${cudnnMajorVer}-${simplyCudaMinorVer}"
                                buildArgs.putAll(mapOf(
                                        "PYTHON_VERSION" to pyFullVer,
                                        "DEBIAN_VERSION" to "$debianVer",
                                        "CUDA_VERSION" to cudaFullVer,
                                        "FLAVOR" to "-playwright${flavor.tagSuffix}",
                                        "CUDNN_VERSION" to cudnnFullVer,
                                        "CUDNN_MAJOR_VERSION" to cudnnMajorVer,
                                        "CUDA_MAJOR_VERSION" to cudaMajorVer,
                                ))
                                inputDir.set(buildDir("cudnn"))
                                images.addAll(cudnnPlaywrightTags)
                                mustRunAfter(buildCudaPlaywright)
                                dependsOn(dockerCreateCudnnDockerfile)
                                dockerFile.set(dockerCreateCudnnDockerfile.destFile)
                                upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudnnPoetryTags.last())
                            }
                            val pushCudnnPlaywright = project.tasks.create(
                                    "push${flavor.taskSuffix}Cuda${simplyCudaMinorVer}Cudnn${cudnnMajorVer}Playwright${simplePyMinorVer}${debianVer.name.capitalized()}Image",
                                    DockerPushImage::class.java
                            ) {
                                group = "cuda${simplyCudaMinorVer}"
                                dependsOn(buildCudnnPlaywright)
                                images.addAll(cudnnPoetryTags)
                                upToDateWhenTagExist(DOCKER_NAMESPACE, DOCKER_REPOSITORY, cudnnPoetryTags.last())
                            }

                            if (project.DOCKER_TOKEN == null) {
                                pushCudnnPoetry.enabled = false
                                pushCudnnPlaywright.enabled = false
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
        private var version: Provider<String>? = null
        val VERSION: String get() = version?.get()!!
    }
}