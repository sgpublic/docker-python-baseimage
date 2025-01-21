package io.github.sgpublic.utils

import org.gradle.api.Project

val Project.DOCKER_TOKEN: String? get() = findEnv("publishing.docker.token").orNull

val Project.dockerHubApi: DockerHubApi get() = DockerHubApi(this)

class DockerHubApi(project: Project): Project by project {
    private val headers: Map<String, String> get() = mapOf(
            "Authorization" to "Bearer $DOCKER_TOKEN"
    )

    fun tagExists(namespace: String, repository: String, tag: String): Boolean {
        val resp = NetResp("$DOCKER_HUB_HOST/v2/namespaces/$namespace/repositories/$repository/tags/$tag", headers)
        return resp.statusCode() == 200
    }

    companion object {
        private const val DOCKER_HUB_HOST = "https://hub.docker.com"
    }
}