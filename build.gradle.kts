import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import io.github.sgpublic.*
import io.github.sgpublic.gradle.VersionGen
import org.gradle.internal.extensions.stdlib.capitalized


plugins {
    alias(poetry.plugins.docker.api)
    alias(poetry.plugins.release.github)
    alias(poetry.plugins.buildsrc.utils)
}

group = "io.github.sgpublic"
val mVersion = "${VersionGen.COMMIT_COUNT_VERSION}"
version = mVersion

tasks {
    val dockerCreatePoetryDockerfile by creating(Dockerfile::class) {
        doFirst {
            delete(layout.buildDirectory.file("poetry"))
            copy {
                from("./src/main/docker")
                into(layout.buildDirectory.dir("poetry/rootf"))
            }
        }
        group = "docker"
        destFile = layout.buildDirectory.file("poetry/Dockerfile")
        arg("PYTHON_VERSION")
        arg("DEBIAN_VERSION")
        from(Dockerfile.From("python:\${PYTHON_VERSION}-slim-\${DEBIAN_VERSION}"))
        runCommand(command(
            replaceSourceListCommand(),
            "apt-get update",
            aptInstall(
                "git",
                "sudo",
                "curl",
                "libfreetype6-dev",
                "build-essential",
                "ffmpeg",
                "gosu",
            ),
        ))
        environmentVariable(mapOf(
            "POETRY_HOME" to "/opt/poetry",
            "POETRY_CACHE_DIR" to "/.cache/poetry",
            "XDG_CACHE_HOME" to "/.cache",
            "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
        ))
        runCommand(command(
            "curl -sSL https://install.python-poetry.org | python3 -",
        ))
        runCommand(command(
            "git config --global --add safe.directory /app",
            "mkdir -p /.cache/poetry",
        ))
        copyFile("./rootf", "/")
        workingDir("/app")
        volume(
            "/.cache",
            "/app"
        )
        environmentVariable(mapOf(
            "PATH" to "\${PATH}:\${POETRY_HOME}/bin",
            "PUID" to "1000",
            "PGID" to "1000",
            "AUTO_VENV" to "0",
            "AUTO_VENV_NAME" to "poetry-runner",
            "AUTO_PIP_INSTALL" to "0",
            "REQUIREMENTS_TXT" to "/app/requirements.txt",
        ))
        entryPoint("bash", "/docker-entrypoint.sh")
    }

    val dockerNamespace = "mhmzx"
    val dockerRepository = "poetry-runner"
    val dockerTagHead = "$dockerNamespace/$dockerRepository"
    val dockerCreatePlaywrightDockerfile by creating(Dockerfile::class) {
        group = "docker"
        arg("PYTHON_VERSION")
        arg("DEBIAN_VERSION")
        arg("PROJECT_VERSION")
        arg("__BREAK_SYSTEM_PACKAGE")
        destFile = layout.buildDirectory.file("playwright/Dockerfile")
        from(Dockerfile.From("$dockerTagHead:\${PYTHON_VERSION}-\${DEBIAN_VERSION}-\${PROJECT_VERSION}"))
        runCommand(command(
            pipInstall(
                "playwright",
                "\${__BREAK_SYSTEM_PACKAGE}"
            ),
            "playwright install-deps chromium",
            "pip uninstall playwright -y",
            "pip cache purge",
        ))
    }

    val dockerToken = findEnv("publishing.docker.token").orNull
    if (dockerToken == null) {
        logger.warn("no docker token provided!")
    }
    for ((version, info) in PythonVersions().versions) {
        for (platform in info.platforms) {
            val simplyVersion = version.replace(".", "")
            val tagsPoetry = listOf(
                "$dockerTagHead:$version-$platform",
                "$dockerTagHead:${info.verName}-$platform",
                "$dockerTagHead:${info.verName}-$platform-$mVersion",
            )
            val buildPoetry = create("dockerBuild${simplyVersion}${platform.name.capitalized()}PoetryImage", DockerBuildImage::class) {
                group = "docker"
                images.addAll(tagsPoetry)
                buildArgs = mapOf(
                    "PYTHON_VERSION" to info.verName,
                    "DEBIAN_VERSION" to "$platform",
                )
                inputDir = layout.buildDirectory.dir("poetry")
                dockerFile = dockerCreatePoetryDockerfile.destFile
                dependsOn(dockerCreatePoetryDockerfile)
            }
            val pushPoetry = create("dockerPush${simplyVersion}${platform.name.capitalized()}PoetryImage", DockerPushImage::class) {
                group = "docker"
                dependsOn(buildPoetry)
                images.addAll(tagsPoetry)
            }

            val tagsPlaywright = listOf(
                "$dockerTagHead:$version-$platform-playwright",
                "$dockerTagHead:${info.verName}-$platform-playwright",
                "$dockerTagHead:${info.verName}-$platform-playwright-$mVersion"
            )
            val buildPlaywright = create("dockerBuild${simplyVersion}${platform.name.capitalized()}PlaywrightImage", DockerBuildImage::class) {
                group = "docker"
                images.addAll(tagsPlaywright)
                dependsOn(dockerCreatePlaywrightDockerfile)
                inputDir = layout.buildDirectory.dir("playwright")
                dockerFile = dockerCreatePlaywrightDockerfile.destFile
                buildArgs = mapOf(
                    "PYTHON_VERSION" to info.verName,
                    "DEBIAN_VERSION" to "$platform",
                    "PROJECT_VERSION" to mVersion,
                )
                if (platform == PythonVersions.Platform.bullseye) {
                    buildArgs.put("__BREAK_SYSTEM_PACKAGE", "")
                } else {
                    buildArgs.put("__BREAK_SYSTEM_PACKAGE", "--break-system-package")
                }
            }
            val pushPlaywright = create("dockerPush${simplyVersion}${platform.name.capitalized()}PlaywrightImage", DockerPushImage::class) {
                group = "docker"
                dependsOn(buildPlaywright)
                images.addAll(tagsPlaywright)
            }

            if (dockerToken == null) {
                pushPoetry.enabled = false
                pushPlaywright.enabled = false
            }
        }
    }

    val createGitTag by creating(GitCreateTag::class) {
        tagName = "v$mVersion"
    }

    val clean by creating(Delete::class) {
        delete(rootProject.file("build"))
    }
}

fun findEnv(name: String) = provider {
    findProperty(name)?.toString()?.takeIf { it.isNotBlank() }
        ?: System.getenv(name.replace(".", "_").uppercase())
}

docker {
    registryCredentials {
        username = findEnv("publishing.docker.username")
        password = findEnv("publishing.docker.password")
    }
}

githubRelease {
    token(findEnv("publishing.github.token"))
    owner = "sgpublic"
    repo = "poetry-docker"
    tagName = "v$mVersion"
    releaseName = "v$mVersion"
    overwrite = true
}
