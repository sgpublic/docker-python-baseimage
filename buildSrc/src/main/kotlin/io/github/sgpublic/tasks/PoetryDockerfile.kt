package io.github.sgpublic.tasks

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import io.github.sgpublic.DockerPlugin
import io.github.sgpublic.utils.*
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class PoetryDockerfile: Dockerfile() {
    @InputDirectory
    fun getInputDir(): java.io.File {
        return project.file("./src/main/docker")
    }

    @OutputDirectory
    fun getOutputDir(): java.io.File {
        return buildDir("poetry").get().asFile
    }

    @TaskAction
    override fun create() {
        project.delete(buildDir("poetry/rootf"))
        project.copy {
            from(getInputDir())
            into(buildDir("poetry/rootf"))
        }

        arg("PYTHON_VERSION")
        arg("DEBIAN_VERSION")
        arg("BASE_FLAVOR")
        from(From("${DockerPlugin.DOCKER_TAG}:\${PYTHON_VERSION}-\${DEBIAN_VERSION}-base\${BASE_FLAVOR}-${DockerPlugin.VERSION}"))
        arg("__SOURCE_LIST_FILE")
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
                "POETRY_CACHE_DIR" to "\$XDG_CACHE_HOME/poetry",
                "PYTHON_KEYRING_BACKEND" to "keyring.backends.null.Keyring",
        ))
        runCommand(command(
                "curl -sSL https://install.python-poetry.org | python3 -",
        ))
        runCommand(command(
                "git config --global --add safe.directory /app",
                "mkdir -p \$XDG_CACHE_HOME/poetry",
        ))
        copyFile("./rootf", "/")
        workingDir("/app")
        volume(
                "/app"
        )
        environmentVariable(mapOf(
                "PATH" to "\${PATH}:\${POETRY_HOME}/bin",
                "AUTO_VENV" to "0",
                "AUTO_VENV_NAME" to "poetry-runner",
                "AUTO_PIP_INSTALL" to "0",
                "REQUIREMENTS_TXT" to "/app/requirements.txt",
        ))

        super.create()
    }

    override fun getGroup(): String {
        return "dockerfile"
    }
}