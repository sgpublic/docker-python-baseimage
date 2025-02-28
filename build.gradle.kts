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
val mVersion = "2.0.0"
version = mVersion

tasks {
    val createGitTag by creating(GitCreateTag::class) {
        tagName = "v$mVersion"
    }

    val baseImageVersion by creating(BaseImageVersion::class)

    val clean by creating(Delete::class) {
        delete(rootProject.file("build"))
    }

    val ciBuild by creating ciBuild@{
        group = "publishing"
        val pyVer = findEnv("ci.build.python.version").orNull
        val debVer = findEnv("ci.build.debian.version").orNull
        val flavor = findEnv("ci.build.baseflavor").orNull?.uppercase()?.let {
            return@let try {
                BaseFlavor.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }

        if (pyVer == null || debVer == null || flavor == null) {
            enabled = false
            return@ciBuild
        }

        val poetryTask = getTasksByName("push${flavor.taskSuffix}Poetry${pyVer}${debVer}Image", false)
                .firstOrNull() ?: return@ciBuild
        dependsOn(poetryTask)
        val playwrightTask = getTasksByName("push${flavor.taskSuffix}Playwright${pyVer}${debVer}Image", false)
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
