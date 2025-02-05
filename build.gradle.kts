import io.github.sgpublic.utils.GitCreateTag
import io.github.sgpublic.gradle.VersionGen
import io.github.sgpublic.utils.findEnv


plugins {
    id("poetry-docker")
    alias(poetry.plugins.release.github)
    alias(poetry.plugins.buildsrc.utils)
}

group = "io.github.sgpublic"
val mVersion = "${VersionGen.COMMIT_COUNT_VERSION}"
version = mVersion

tasks {
    val createGitTag by creating(GitCreateTag::class) {
        tagName = "v$mVersion"
    }

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

        val poetryTask = getTasksByName("pushPoetry${pyVer}${debVer}Image", false)
                .firstOrNull() ?: return@ciBuild
        dependsOn(poetryTask)
        val playwrightTask = getTasksByName("pushPlaywright${pyVer}${debVer}Image", false)
                .firstOrNull() ?: return@ciBuild
        dependsOn(playwrightTask)
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
    repo = "poetry-docker"
    tagName = "v$mVersion"
    releaseName = "v$mVersion"
    overwrite = true
}
