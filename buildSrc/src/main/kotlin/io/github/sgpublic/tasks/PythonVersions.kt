package io.github.sgpublic.tasks

import com.google.gson.JsonObject
import io.github.g00fy2.versioncompare.Version
import io.github.sgpublic.utils.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class PythonVersions: DefaultTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun execute() {
        if (project.DOCKER_TOKEN == null) {
            logger.error("please provide a docker token to continue!")
            return
        }

        val checkedVersions = LinkedHashMap<String, Map<DebianVersion, String>>()
        val dockerHubApi = project.dockerHubApi

        val versions = NetJsonArray(VERSIONS_RSS)
        for (version in versions) {
            if (version !is JsonObject) {
                continue
            }
            if (!version.getAsJsonPrimitive("stable").asBoolean) {
                // 非稳定版本跳过
                continue
            }
            val curVer = Version(version.getAsJsonPrimitive("version").asString)
            if (curVer < MIN_VER) {
                // 放弃支持 3.8.x 及以下
                continue
            }
            // 仅存储每个次要版本的最新版本
            val minorVer = Version("${curVer.major}.${curVer.minor}")
            val storedMinorVer = LinkedHashMap<DebianVersion, String>()
            checkedVersions[minorVer.toString()]?.let(storedMinorVer::putAll)
            for (platform in DebianVersion.values()) {
                val storedVer = storedMinorVer[platform]
                Thread.sleep(10)
                logger.debug("[PythonVersions] checking {}-{}...", curVer, platform)
                if (!dockerHubApi.tagExists("library", "python", "${curVer}-${platform}")) {
                    continue
                }
                if (storedVer == null || Version(storedVer) < curVer) {
                    storedMinorVer[platform] = curVer.toString()
                }
            }
            checkedVersions[minorVer.toString()] = storedMinorVer
        }

        project.PythonVersionsInfo(VersionsInfo(checkedVersions))
    }

    override fun getGroup(): String {
        return "python"
    }

    companion object {
        const val VERSIONS_RSS = "https://raw.githubusercontent.com/actions/python-versions/refs/heads/main/versions-manifest.json"
        private val MIN_VER = Version("3.9")
    }
}