package io.github.sgpublic.tasks

import io.github.g00fy2.versioncompare.Version
import io.github.sgpublic.utils.DebianVersion
import io.github.sgpublic.utils.NetString
import io.github.sgpublic.utils.CudaVersionsInfo
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import kotlin.collections.LinkedHashMap

data class CudaVersion(
        val debian: LinkedHashMap<DebianVersion, String> = LinkedHashMap(),
        val cudnn: LinkedHashMap<String, String> = LinkedHashMap(),
)

open class CodaVersions: DefaultTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun execute() {
        val versions = LinkedHashMap<String, CudaVersion>()
        for (debianVersion in DebianVersion.values()) {
            versionRss(debianVersion, versions)
        }
        project.CudaVersionsInfo(CudaVersionsInfo(versions.toSortedMap()))
    }

    private fun versionRss(debian: DebianVersion, versions: LinkedHashMap<String, CudaVersion>) {
        val cudaMatch = "Package: cuda-\\d+-\\d+".toRegex()
        val cudnnMatch = "Package: cudnn\\d+-cuda-\\d+-\\d+".toRegex()
        val cudnnMajorMatch = "cudnn\\d+".toRegex()
        val versionMatch = "Version: (.*?)-1".toRegex()
        val versionsRss = NetString("https://developer.download.nvidia.com/compute/cuda/repos/debian${debian.numVer}/x86_64/Packages")
                .split("\n\n")
        val cudaVersionRss = versionsRss.filter { it.contains(cudaMatch) }
        val cudnnVersionRss = versionsRss.filter { it.contains(cudnnMatch) }
        for (version in cudaVersionRss) {
            // cuda
            val details = version.split("\n")
            val cudaMinerRaw = cudaMatch.find(details[0])?.value?.let {
                return@let it.subSequence(14, it.length).toString()
            } ?: continue
            val cudaMiner = cudaMinerRaw.replace("-", ".")
            val cudaMinerInfo = versions[cudaMiner] ?: CudaVersion()
            val cudaFullVer = Version(
                    versionMatch.find(details[1])?.value?.let {
                        return@let it.subSequence(9, it.length - 2).toString()
                    } ?: continue
            )
            val existCudaVer = cudaMinerInfo.debian[debian]
            if (existCudaVer == null || Version(existCudaVer) < cudaFullVer) {
                cudaMinerInfo.debian[debian] = cudaFullVer.toString()
            }

            // cudnn
            val cudnnItems = cudnnVersionRss.filter { it.contains("cuda-${cudaMinerRaw}") }
            for (cudnnItem: String in cudnnItems) {
                val cudnnDetails = cudnnItem.split("\n")
                val cudnnMajor = cudnnMajorMatch.find(cudnnDetails[0])
                        ?.value?.substring(5)
                        ?: continue
                val cudnnFullVer = Version(
                        versionMatch.find(cudnnDetails[1])?.value?.let {
                            return@let it.subSequence(9, it.length - 2).toString()
                        } ?: continue
                )
                val existCudnnVer = cudaMinerInfo.cudnn[cudnnMajor]
                if (existCudnnVer == null || Version(existCudnnVer) < cudnnFullVer) {
                    cudaMinerInfo.cudnn[cudnnMajor] = cudnnFullVer.toString()
                }
            }

            versions[cudaMiner] = cudaMinerInfo
        }
    }

    override fun getGroup(): String {
        return "cuda"
    }
}