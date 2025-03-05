plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.download.gradle.plugin)
    implementation(libs.gson)
    implementation(libs.jgit) {
        exclude("com.jcraft", "jsch")
    }
    implementation(libs.jgit.apache)
    implementation(libs.bundles.docker.java)
    implementation(libs.docker.api) {
        exclude("com.github.docker-java")
    }
    implementation(libs.version.compare)
}

gradlePlugin {
    plugins {
        val `poetry-docker` by creating {
            id = "poetry-docker"
            implementationClass = "io.github.sgpublic.DockerPlugin"
        }
    }
}
