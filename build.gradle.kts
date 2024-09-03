import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import io.github.sgpublic.*
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
            if (!adbDir.get().asFile.exists()) {
                mkdir(adbDir)
            }
        }
        doLast {
            if (!File(adbDir.get().asFile, "platform-tools").exists()) {
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
    }

    val username = "poetry-runner"
    val dockerCreateBookwormDockerfile by creating(Dockerfile::class) {
        dependsOn(downloadLatestAdb)
        doFirst {
            delete(layout.buildDirectory.file("docker-bookworm"))
            copy {
                from("./src/main/docker")
                into(layout.buildDirectory.dir("docker-bookworm/rootf"))
            }
            copy {
                from(layout.buildDirectory.dir("adb/platform-tools"))
                into(layout.buildDirectory.dir("docker-bookworm/rootf/opt/adb"))
            }
        }
        group = "docker"
        destFile = layout.buildDirectory.file("docker-bookworm/Dockerfile")
        arg("PYTHON_VERSION")
        from(Dockerfile.From("python:\${PYTHON_VERSION}-bookworm"))
        runCommand(command(
            "apt-get update",
            aptInstall(
                "python3-pip",
                "python3-venv",
                "python3-wheel",
                "git",
                "sudo",
                "ffmpeg",
                "curl",
                "libfreetype6-dev",
                "python3-tk",
                "android-sdk-platform-tools-common",
            ),
        ))
        environmentVariable(mapOf(
            "ADB_HOME" to "/opt/adb",
            "POETRY_HOME" to "/opt/poetry",
            "POETRY_CACHE_DIR" to "/home/$username/.cache/poetry",
            "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
            "RUSTUP_HOME" to "/opt/rustup",
            "CARGO_HOME" to "/opt/cargo",
        ))
        runCommand(command(
            "curl -sSL https://install.python-poetry.org | python3 -",
        ))
        runCommand(command(
            "pip install playwright --break-system-packages",
        ))
        runCommand(command(
            "playwright install-deps",
        ))
        runCommand(command(
            "git config --global --add safe.directory /app",
            "useradd -m -u 1000 $username",
            "mkdir -p /home/$username/.cache",
            "chown -R $username:$username /home/$username/.cache",
            "usermod -aG plugdev $username",
        ))
        runCommand(command(
            "mkdir -p \$RUSTUP_HOME \$CARGO_HOME",
            "chown $username:$username \$RUSTUP_HOME \$CARGO_HOME",
        ))
        user(username)
        runCommand(command(
            "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y",
        ))
        user("root")
        copyFile("./rootf", "/")
        workingDir("/app")
        volume(
            "/home/$username/.cache",
            "/app"
        )
        entryPoint("bash", "/docker-entrypoint.sh")
    }

    val dockerCreateBullseyeDockerfile by creating(Dockerfile::class) {
        dependsOn(downloadLatestAdb)
        doFirst {
            delete(layout.buildDirectory.file("docker-bullseye"))
            copy {
                from("./src/main/docker")
                into(layout.buildDirectory.dir("docker-bullseye/rootf"))
            }
            copy {
                from(layout.buildDirectory.dir("adb/platform-tools"))
                into(layout.buildDirectory.dir("docker-bullseye/rootf/opt/adb"))
            }
        }
        group = "docker"
        destFile = layout.buildDirectory.file("docker-bullseye/Dockerfile")
        arg("PYTHON_VERSION")
        from(Dockerfile.From("python:\${PYTHON_VERSION}-bullseye"))
        runCommand(command(
            "apt-get update",
            aptInstall(
                "pkg-config",
                "python3-pip",
                "python3-venv",
                "git",
                "sudo",
                "ffmpeg",
                "curl",
                "libfreetype6-dev",
                "python3-tk",
                "android-sdk-platform-tools-common",
            ),
        ))
        environmentVariable(mapOf(
            "ADB_HOME" to "/opt/adb",
            "POETRY_HOME" to "/opt/poetry",
            "POETRY_CACHE_DIR" to "/home/$username/.cache/poetry",
            "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
            "RUSTUP_HOME" to "/opt/rustup",
            "CARGO_HOME" to "/opt/cargo",
        ))
        runCommand(command(
            "curl -sSL https://install.python-poetry.org | python3 -",
        ))
        runCommand(command(
            "pip install wheel playwright",
        ))
        runCommand(command(
            "playwright install-deps",
        ))
        runCommand(command(
            "git config --global --add safe.directory /app",
            "useradd -m -u 1000 $username",
            "mkdir -p /home/$username/.cache",
            "chown -R $username:$username /home/$username/.cache",
            "usermod -aG plugdev $username",
        ))
        runCommand(command(
            "mkdir -p \$RUSTUP_HOME \$CARGO_HOME",
            "chown $username:$username \$RUSTUP_HOME \$CARGO_HOME",
        ))
        user(username)
        runCommand(command(
            "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y",
        ))
        user("root")
        copyFile("./rootf", "/")
        workingDir("/app")
        volume(
            "/home/$username/.cache",
            "/app"
        )
        entryPoint("bash", "/docker-entrypoint.sh")
    }

    val dockerNamespace = "mhmzx"
    val dockerRepository = "poetry-runner"
    val dockerTagHead = "$dockerNamespace/$dockerRepository"
    val dockerToken = findEnv("publishing.docker.token").orNull
    if (dockerToken == null) {
        logger.warn("no docker token provided!")
    }
    val builds = mutableSetOf<DockerBuildImage>()
    val pushs = mutableSetOf<DockerPushImage>()
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
                    builds.add(build)
                    if (dockerToken != null) {
                        pushs.add(push)
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
        for (task in builds) {
            dependsOn(task)
        }
    }
    if (dockerToken != null) {
        val dockerPushAbsenceImage by creating {
            group = "docker"
            for (task in pushs) {
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
