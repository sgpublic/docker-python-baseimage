import io.github.sgpublic.BaseFlavor
import io.github.sgpublic.tasks.GitCreateTag
import io.github.sgpublic.tasks.BaseImageVersion
import io.github.sgpublic.gradle.VersionGen
import io.github.sgpublic.utils.findEnv


plugins {
    id("poetry-docker")
    alias(poetry.plugins.release.github)
    alias(poetry.plugins.buildsrc.utils)
}

group = "io.github.sgpublic"
val mVersion = findEnv("project.version")
version = mVersion

tasks {
    val createGitTag by creating(GitCreateTag::class) {
        tagName = provider { "v${mVersion.get()}" }
    }

    val baseImageVersion by creating(BaseImageVersion::class)

    val clean by creating(Delete::class) {
        delete(rootProject.file("build"))
    }

    val ciBuild by creating ciBuild@{
        group = "publishing"
        val pyVer = findEnv("ci.build.python.version").orNull
        val debVer = findEnv("ci.build.debian.version").orNull

        if (pyVer == null || debVer == null) {
            enabled = false
            return@ciBuild
        }

        getTasksByName("push${BaseFlavor.COMMON.taskSuffix}${pyVer}${debVer}Image", false)
                .firstOrNull()?.let { dependsOn(it) }
        getTasksByName("push${BaseFlavor.GUI.taskSuffix}${pyVer}${debVer}Image", false)
                .firstOrNull()?.let { dependsOn(it) }
        getTasksByName("push${BaseFlavor.COMMON.taskSuffix}Poetry${pyVer}${debVer}Image", false)
                .firstOrNull()?.let { dependsOn(it) }
        getTasksByName("push${BaseFlavor.COMMON.taskSuffix}Playwright${pyVer}${debVer}Image", false)
                .firstOrNull()?.let { dependsOn(it) }
    }
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
    repo = "docker-python-baseimage"
    tagName = provider { "v${mVersion.get()}" }
    releaseName = provider { "v${mVersion.get()}" }
    overwrite = true
}
