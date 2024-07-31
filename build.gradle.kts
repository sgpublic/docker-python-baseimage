import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    alias(poetry.plugins.docker.api)
    alias(poetry.plugins.release.github)
}

group = "io.github.sgpublic"
// https://hub.docker.com/_/debian/tags
version = "20240722"

tasks {
    val tag = "mhmzx/poetry-runner"

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
        from(Dockerfile.From("debian:bookworm-$version").withStage("builder"))
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
            "echo \"export PATH=\\\$PATH:\\\$ADB_HOME\" >> /etc/profile",
            "echo \"# poetry\" >> /etc/profile",
            "echo \"export PATH=\\\$PATH:\\\$POETRY_HOME/bin\" >> /etc/profile",
            "usermod -aG plugdev poetry-runner",
            "apt-get clean",
            "pip cache purge",
            "rm -rf /home/poetry-runner/.cache/* /usr/share/fonts/*",
        ).joinToString(" &&\\\n "))

        from(Dockerfile.From("debian:bookworm-$version"))
        environmentVariable(mapOf(
            "POETRY_HOME" to "/usr/share/poetry",
            "POETRY_CACHE_DIR" to "/home/poetry-runner/.cache/poetry",
            "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
            "ADB_HOME" to "/usr/share/adb",
        ))
        copyFile(Dockerfile.CopyFile("/", "/").withStage("builder"))
        copyFile("./*.sh", "/")
        copyFile("./adb", "/usr/share/adb")
        workingDir("/app")
        volume("/home/poetry-runner/.cache")
        volume("/app")
        entryPoint("bash", "/docker-entrypoint.sh")
    }
    val dockerBuildBookwormImage by creating(DockerBuildImage::class) {
        group = "docker"
        dependsOn(dockerCreateBookwormDockerfile)
        inputDir = layout.buildDirectory.dir("docker-bookworm")
        dockerFile = dockerCreateBookwormDockerfile.destFile
        images.add("$tag:bookworm-$version")
        images.add("$tag:bookworm-latest")
        noCache = true
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
        from(Dockerfile.From("debian:bullseye-$version").withStage("builder"))
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
                "pip install playwright",
                "playwright install-deps",
                "git config --global --add safe.directory /app",
                "useradd -m -u 1000 poetry-runner",
                "mkdir -p /home/poetry-runner/.cache",
                "chown -R poetry-runner:poetry-runner /home/poetry-runner/.cache",
                "echo \"# adb\" >> /etc/profile",
                "echo \"export PATH=\\\$PATH:/bin/adb\" >> /etc/profile",
                "apt-get clean",
                "pip cache purge",
                "rm -rf /var/cache/* /var/tmp/* /home/poetry-runner/.cache/*",
            ).joinToString(" &&\\\n ")
        )

        from(Dockerfile.From("debian:bullseye-$version"))
        environmentVariable(mapOf(
            "POETRY_HOME" to "/usr/share/poetry",
            "POETRY_CACHE_DIR" to "/home/poetry-runner/.cache/poetry",
            "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
            "ADB_HOME" to "/usr/share/adb",
        ))
        copyFile(Dockerfile.CopyFile("/", "/").withStage("builder"))
        copyFile("./*.sh", "/")
        copyFile("./adb", "/bin/adb")
        workingDir("/app")
        volume("/home/poetry-runner/.cache")
        volume("/app")
        entryPoint("bash", "/docker-entrypoint.sh")
    }
    val dockerBuildBullseyeImage by creating(DockerBuildImage::class) {
        group = "docker"
        dependsOn(dockerCreateBullseyeDockerfile)
        inputDir = layout.buildDirectory.dir("docker-bullseye")
        dockerFile = dockerCreateBullseyeDockerfile.destFile
        images.add("$tag:bullseye-$version")
        images.add("$tag:bullseye-latest")
        noCache = true
    }

    val dockerBuildImage by creating {
        group = "docker"
        dependsOn(dockerBuildBookwormImage, dockerBuildBullseyeImage)
    }

    val dockerPushBuildBookImageOfficial by creating(DockerPushImage::class) {
        group = "docker"
        dependsOn(dockerBuildBookwormImage)
        images.add("$tag:bookworm-$version")
        images.add("$tag:bookworm-latest")
    }

    val dockerPushBullseyeImageOfficial by creating(DockerPushImage::class) {
        group = "docker"
        dependsOn(dockerBuildBullseyeImage)
        images.add("$tag:bullseye-$version")
        images.add("$tag:bullseye-latest")
    }

    val dockerPushImageOfficial by creating {
        group = "docker"
        dependsOn(dockerPushBuildBookImageOfficial, dockerPushBullseyeImageOfficial)
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
    tagName = "$version"
    releaseName = "$version"
    overwrite = true
}
