plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.download.gradle.plugin)
    implementation(libs.gson)
    implementation(libs.jgit) {
        exclude("com.jcraft", "jsch")
    }
    implementation(libs.jgit.apache)
}