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
