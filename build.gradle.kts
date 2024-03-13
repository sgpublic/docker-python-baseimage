import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    alias(backsql.plugins.docker.api)
    alias(backsql.plugins.release.github)
}

group = "io.github.sgpublic"
version = "20240311"

tasks {
    val tag = "mhmzx/poetry-runner"

    val dockerCreateBookwormDockerfile by creating(Dockerfile::class) {
        doFirst {
            delete(layout.buildDirectory.file("docker-bookworm"))
            copy {
                from("./src/main/docker/")
                include("*.sh")
                into(layout.buildDirectory.dir("docker-bookworm"))
            }
        }
        group = "docker"
        destFile = layout.buildDirectory.file("docker-bookworm/Dockerfile")
        from("debian:bookworm-$version")
        workingDir("/app")
        copyFile("./*.sh", "/")
        runCommand(listOf(
                "apt-get update",
                "apt-get install python3-pip python3-poetry python3-venv git libfreetype6-dev python-tk -y",
                "pip install pipx --break-system-packages",
                "pipx run playwright install-deps",
                "git config --global --add safe.directory /app",
                "useradd -m -u 1000 poetry-runner",
                "mkdir -p /home/poetry-runner/.cache",
                "chown -R poetry-runner:poetry-runner /home/poetry-runner/.cache",
        ).joinToString(" &&\\\n "))
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
        doFirst {
            delete(layout.buildDirectory.file("docker-bullseye"))
            copy {
                from("./src/main/docker/")
                include("*.sh")
                into(layout.buildDirectory.file("docker-bullseye"))
            }
        }
        group = "docker"
        destFile = layout.buildDirectory.file("docker-bullseye/Dockerfile")
        from("debian:bullseye-$version")
        workingDir("/app")
        copyFile("./*.sh", "/")
        runCommand(listOf(
                "apt-get update",
                "apt-get install pkg-config -y",
                "apt-get install python3-pip python3-venv git libfreetype6-dev python-tk -y",
                "pip install poetry pipx",
                "pipx run playwright install-deps",
                "git config --global --add safe.directory /app",
                "useradd -m -u 1000 poetry-runner",
                "mkdir -p /home/poetry-runner/.cache",
                "chown -R poetry-runner:poetry-runner /home/poetry-runner/.cache",
        ).joinToString(" &&\\\n "))
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

fun findEnv(name: String): String {
    return findProperty(name)?.toString()?.takeIf { it.isNotBlank() }
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
