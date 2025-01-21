package io.github.sgpublic.tasks

import io.github.g00fy2.versioncompare.Version
import io.github.sgpublic.utils.DebianVersion
import io.github.sgpublic.utils.NetString
import io.github.sgpublic.utils.CudaVersionsInfo
import io.github.sgpublic.utils.VersionsInfo
import io.github.sgpublic.utils.choseByTimezone
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

open class CodaVersions: DefaultTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun execute() {
        val versions = LinkedHashMap<String, Map<DebianVersion, String>>()
        for (debianVersion in DebianVersion.values()) {
            versionRss(debianVersion, versions)
        }
        project.CudaVersionsInfo(VersionsInfo(versions.toSortedMap()))
    }

    private fun versionRss(debian: DebianVersion, versions: LinkedHashMap<String, Map<DebianVersion, String>>) {
        val packageMatch = "Package: cuda-\\d+-\\d+".toRegex()
        val versionMatch = "Version: \\d+.\\d+.\\d+-1".toRegex()
        val versionsRss = NetString("https://developer.download.nvidia.com/compute/cuda/repos/debian${debian.numVer}/x86_64/Packages")
                .split("\n\n").filter { it.contains(packageMatch) }
        for (version in versionsRss) {
            val details = version.split("\n")
            val minerVersion = packageMatch.find(details[0])?.value?.let {
                return@let it.subSequence(14, it.length).toString()
            }?.replace("-", ".") ?: continue
            val minerVersionInfo = LinkedHashMap<DebianVersion, String>(versions[minerVersion] ?: emptyMap())
            val fullVersion = Version(
                    versionMatch.find(details[1])?.value?.let {
                        return@let it.subSequence(9, it.length - 2).toString()
                    } ?: continue
            )
            val storedVersion = minerVersionInfo[debian]
            if (storedVersion == null || Version(storedVersion) < fullVersion) {
                minerVersionInfo[debian] = fullVersion.toString()
            }
            versions[minerVersion] = minerVersionInfo
        }
    }

    override fun getGroup(): String {
        return "cuda"
    }
}