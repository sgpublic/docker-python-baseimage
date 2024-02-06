import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    alias(backsql.plugins.docker.api)
    alias(backsql.plugins.release.github)
}

group = "io.github.sgpublic"
version = "20240130"

tasks {
    fun Dockerfile.applyPoetryRunnerDockerfile(target: String) {
        group = "docker"
        destFile = layout.buildDirectory.file("docker/dockerfile-$target")
        from("debian:$target-$version-slim")
        workingDir("/app")
        copyFile("./src/main/docker/*.sh", "/")
        runCommand(listOf(
                "apt-get update",
                "apt-get install python3-pip python3-poetry python3-venv git libfreetype6-dev -y",
                "git config --global --add safe.directory /app",
                "useradd -m -u 1000 poetry-runner",
                "mkdir -p /home/poetry-runner/.cache",
                "chown -R poetry-runner:poetry-runner /home/poetry-runner/.cache",
        ).joinToString(" &&\\\n "))
        volume("/home/poetry-runner/.cache")
        volume("/app")
        entryPoint("bash", "/docker-entrypoint.sh")
    }

    val tag = "mhmzx/poetry-runner"
    fun DockerBuildImage.applyPoetryRunnerBuildDocker(target: String, dependsOn: Dockerfile) {
        group = "docker"
        dependsOn(dependsOn)
        inputDir = project.file("./")
        dockerFile = dependsOn.destFile
        images.add("$tag:$target-$version")
        images.add("$tag:$target-latest")
        noCache = true
    }

    val dockerCreateBookwormDockerfile by creating(Dockerfile::class) {
        applyPoetryRunnerDockerfile("bookworm")
    }
    val dockerBuildBookwormImage by creating(DockerBuildImage::class) {
        applyPoetryRunnerBuildDocker("bookworm", dockerCreateBookwormDockerfile)
    }

    val dockerCreateBullseyeDockerfile by creating(Dockerfile::class) {
        applyPoetryRunnerDockerfile("bullseye")
    }
    val dockerBuildBullseyeImage by creating(DockerBuildImage::class) {
        applyPoetryRunnerBuildDocker("bullseye", dockerCreateBullseyeDockerfile)
    }

    val dockerBuildImage by creating {
        group = "docker"
        dependsOn(dockerBuildBookwormImage, dockerBuildBullseyeImage)
    }

    val dockerPushImageOfficial by creating(DockerPushImage::class) {
        group = "docker"
        dependsOn(dockerBuildImage)
        images.add("$tag:bookworm-$version")
        images.add("$tag:bookworm-latest")
        images.add("$tag:bullseye-$version")
        images.add("$tag:bullseye-latest")
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
