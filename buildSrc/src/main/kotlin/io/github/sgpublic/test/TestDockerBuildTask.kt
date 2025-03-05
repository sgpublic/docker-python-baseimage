package io.github.sgpublic.test

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class TestDockerBuildTask: DefaultTask() {
    @TaskAction
    fun action() {
        val custom = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .build()
        val client = ApacheDockerHttpClient.Builder()
                .dockerHost(custom.dockerHost)
                .build()
        client.use {
            
        }
    }
}