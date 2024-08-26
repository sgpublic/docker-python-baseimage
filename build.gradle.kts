import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import io.github.sgpublic.GitCreateTag
import io.github.sgpublic.PythonVersions
import io.github.sgpublic.gradle.VersionGen
import org.gradle.internal.extensions.stdlib.capitalized
import java.net.HttpURLConnection
import java.net.URL

plugins {
    alias(poetry.plugins.docker.api)
    alias(poetry.plugins.release.github)
    alias(poetry.plugins.buildsrc.utils)
}

group = "io.github.sgpublic"
val mVersion = "${VersionGen.COMMIT_COUNT_VERSION}"
version = mVersion

tasks {
    val downloadLatestAdb by creating {
        val adbDir = layout.buildDirectory.dir("adb")
        doFirst {
            if (adbDir.get().asFile.exists()) {
                delete(adbDir)
            }
            mkdir(adbDir)
        }
        doLast {
            exec {
                workingDir(adbDir)
                commandLine("wget", "https://dl.google.com/android/repository/platform-tools-latest-linux.zip")
            }
            exec {
                workingDir(adbDir)
                commandLine("unzip", "platform-tools-latest-linux.zip")
            }
        }
    }

    val dockerCreateBookwormDockerfile by creating(Dockerfile::class) {
        dependsOn(downloadLatestAdb)
        doFirst {
            delete(layout.buildDirectory.file("docker-bookworm"))
            copy {
                from("./src/main/docker/")
                include("*.sh")
                into(layout.buildDirectory.dir("docker-bookworm"))
            }
            copy {
                from(layout.buildDirectory.dir("adb/platform-tools"))
                into(layout.buildDirectory.dir("docker-bookworm/adb"))
            }
        }
        group = "docker"
        destFile = layout.buildDirectory.file("docker-bookworm/Dockerfile")
        arg("PYTHON_VERSION")
        from(Dockerfile.From("python:\${PYTHON_VERSION}-bookworm").withStage("builder"))
        environmentVariable(mapOf(
            "POETRY_HOME" to "/usr/share/poetry",
            "POETRY_CACHE_DIR" to "/home/poetry-runner/.cache/poetry",
            "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
        ))
        runCommand(listOf(
            "apt-get update",
            "apt-get install -y " +
                    "python3-pip " +
                    "python3-venv " +
                    "python3-wheel " +
                    "git " +
                    "sudo " +
                    "ffmpeg " +
                    "curl " +
                    "libfreetype6-dev " +
                    "python3-tk " +
                    "android-sdk-platform-tools-common",
            "[ ! -f /usr/bin/python ] && ln -s /usr/bin/python3 /usr/bin/python",
            "curl -sSL https://install.python-poetry.org | python3 - || { cat /poetry-installer-error-*.log; exit 1; }",
            "pip install playwright --break-system-packages",
            "playwright install-deps",
            "git config --global --add safe.directory /app",
            "useradd -m -u 1000 poetry-runner",
            "mkdir -p /home/poetry-runner/.cache",
            "chown -R poetry-runner:poetry-runner /home/poetry-runner/.cache",
            "echo \"# adb\" >> /etc/profile",
            "echo \"export PATH=\\\$PATH:/usr/share/adb\" >> /etc/profile",
            "echo \"# poetry\" >> /etc/profile",
            "echo \"export PATH=\\\$PATH:\\\$POETRY_HOME/bin\" >> /etc/profile",
            "usermod -aG plugdev poetry-runner",
            "apt-get clean",
            "pip cache purge",
            "rm -rf /home/poetry-runner/.cache/* /usr/share/fonts/*",
        ).joinToString(" &&\\\n "))

        from(Dockerfile.From("python:\${PYTHON_VERSION}-bookworm"))
        environmentVariable(mapOf(
            "POETRY_HOME" to "/usr/share/poetry",
            "POETRY_CACHE_DIR" to "/home/poetry-runner/.cache/poetry",
            "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
        ))
        copyFile(Dockerfile.CopyFile("/", "/").withStage("builder"))
        copyFile("./*.sh", "/")
        copyFile("./adb", "/usr/share/adb")
        workingDir("/app")
        volume("/home/poetry-runner/.cache")
        volume("/app")
        entryPoint("bash", "/docker-entrypoint.sh")
    }

    val dockerCreateBullseyeDockerfile by creating(Dockerfile::class) {
        dependsOn(downloadLatestAdb)
        doFirst {
            delete(layout.buildDirectory.file("docker-bullseye"))
            copy {
                from("./src/main/docker/")
                include("*.sh")
                into(layout.buildDirectory.file("docker-bullseye"))
            }
            copy {
                from(layout.buildDirectory.dir("adb/platform-tools"))
                into(layout.buildDirectory.dir("docker-bullseye/adb"))
            }
        }
        group = "docker"
        destFile = layout.buildDirectory.file("docker-bullseye/Dockerfile")
        arg("PYTHON_VERSION")
        from(Dockerfile.From("python:\${PYTHON_VERSION}-bullseye").withStage("builder"))
        environmentVariable(mapOf(
            "POETRY_HOME" to "/usr/share/poetry",
            "POETRY_CACHE_DIR" to "/home/poetry-runner/.cache/poetry",
            "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
        ))
        runCommand(
            listOf(
                "apt-get update",
                "apt-get install pkg-config -y",
                "apt-get install -y " +
                        "python3-pip " +
                        "python3-venv " +
                        "git " +
                        "sudo " +
                        "ffmpeg " +
                        "curl " +
                        "libfreetype6-dev " +
                        "python3-tk " +
                        "android-sdk-platform-tools-common",
                "[ ! -f /usr/bin/python ] && ln -s /usr/bin/python3 /usr/bin/python",
                "curl -sSL https://install.python-poetry.org | python3 - || { cat /poetry-installer-error-*.log; exit 1; }",
                "pip install wheel playwright",
                "playwright install-deps",
                "git config --global --add safe.directory /app",
                "useradd -m -u 1000 poetry-runner",
                "mkdir -p /home/poetry-runner/.cache",
                "chown -R poetry-runner:poetry-runner /home/poetry-runner/.cache",
                "echo \"# adb\" >> /etc/profile",
                "echo \"export PATH=\\\$PATH:/usr/share/adb\" >> /etc/profile",
                "apt-get clean",
                "pip cache purge",
                "rm -rf /var/cache/* /var/tmp/* /home/poetry-runner/.cache/*",
            ).joinToString(" &&\\\n ")
        )

        from(Dockerfile.From("python:\${PYTHON_VERSION}-bullseye"))
        environmentVariable(mapOf(
            "POETRY_HOME" to "/usr/share/poetry",
            "POETRY_CACHE_DIR" to "/home/poetry-runner/.cache/poetry",
            "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
        ))
        copyFile(Dockerfile.CopyFile("/", "/").withStage("builder"))
        copyFile("./*.sh", "/")
        copyFile("./adb", "/usr/share/adb")
        workingDir("/app")
        volume("/home/poetry-runner/.cache")
        volume("/app")
        entryPoint("bash", "/docker-entrypoint.sh")
    }

    val dockerNamespace = "mhmzx"
    val dockerRepository = "poetry-runner"
    val dockerTagHead = "$dockerNamespace/$dockerRepository"
    val dockerToken = findEnv("publishing.docker.token").orNull
    if (dockerToken == null) {
        logger.warn("no docker token provided!")
    }
    val builds = mutableMapOf<PythonVersions.VersionInfo, DockerBuildImage>()
    val pushs = mutableMapOf<PythonVersions.VersionInfo, DockerPushImage>()
    for ((version, info) in PythonVersions().versions) {
        for (platform in info.platforms) {
            val fullTag = "$dockerTagHead:${info.verName}-$platform-$mVersion"
            val tags = listOf(
                "$dockerTagHead:$version-$platform",
                "$dockerTagHead:${info.verName}-$platform",
                "$dockerTagHead:$version-$platform-$mVersion",
                fullTag,
            )
            val build = create("dockerBuild${info.verName}${platform.name.capitalized()}Image", DockerBuildImage::class) {
                group = "docker"
                images.addAll(tags)
                buildArgs = mapOf(
                    "PYTHON_VERSION" to info.verName,
                )
                when (platform) {
                    PythonVersions.Platform.bookworm -> {
                        dependsOn(dockerCreateBookwormDockerfile)
                        inputDir = layout.buildDirectory.dir("docker-bookworm")
                        dockerFile = dockerCreateBookwormDockerfile.destFile
                    }
                    PythonVersions.Platform.bullseye -> {
                        dependsOn(dockerCreateBullseyeDockerfile)
                        inputDir = layout.buildDirectory.dir("docker-bullseye")
                        dockerFile = dockerCreateBullseyeDockerfile.destFile
                    }
                }
                noCache = true
            }
            val push = create("dockerPush${info.verName}${platform.name.capitalized()}Image", DockerPushImage::class) {
                group = "docker"
                dependsOn(build)
                images.addAll(tags)
            }

            try {
                val connection = URL("https://hub.docker.com/v2/namespaces/$dockerNamespace/repositories/$dockerRepository/tags/$fullTag")
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                dockerToken?.let { token ->
                    connection.addRequestProperty("Authorization", "Bearer $token")
                }

                if (connection.responseCode == 404) {
                    logger.warn("tag \"$fullTag\" is absence, prepare for building tasks...")
                    builds[info] = build
                    if (dockerToken != null) {
                        pushs[info] = push
                    }
                } else {
                    logger.info("tag \"$fullTag\" exist, skip.")
                }

                connection.disconnect()
            } catch (e: Exception) {
                logger.error("Failed to check docker tag: $fullTag")
            }
        }
    }

    val dockerBuildAbsenceImage by creating {
        group = "docker"
        for ((_, task) in builds) {
            dependsOn(task)
        }
    }
    if (dockerToken != null) {
        val dockerPushAbsenceImage by creating {
            group = "docker"
            for ((_, task) in pushs) {
                dependsOn(task)
            }
        }
    }

    val createGitTag by creating(GitCreateTag::class) {
        tagName = "v$mVersion"
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
