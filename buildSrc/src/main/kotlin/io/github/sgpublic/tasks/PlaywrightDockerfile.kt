package io.github.sgpublic.tasks

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import io.github.sgpublic.DockerPlugin
import io.github.sgpublic.utils.*
import org.gradle.api.tasks.TaskAction

open class PlaywrightDockerfile : Dockerfile() {
    @TaskAction
    override fun create() {
        arg("PYTHON_VERSION")
        arg("DEBIAN_VERSION")
        arg("BASE_FLAVOR")
        from(From("${DockerPlugin.DOCKER_TAG}:\${PYTHON_VERSION}-\${DEBIAN_VERSION}\${BASE_FLAVOR}"))
        arg("__BREAK_SYSTEM_PACKAGE")
        runCommand(command(
                pipInstall(
                        "playwright",
                        "\${__BREAK_SYSTEM_PACKAGE}"
                ),
                "playwright install-deps chromium",
                "pip uninstall playwright -y",
                "pip cache purge",
        ))
        super.create()
    }

    override fun getGroup(): String {
        return "dockerfile"
    }
}