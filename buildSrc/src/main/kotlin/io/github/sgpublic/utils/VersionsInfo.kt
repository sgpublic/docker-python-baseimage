package io.github.sgpublic.utils

import org.gradle.api.Project
import org.gradle.internal.extensions.stdlib.capitalized
import java.io.File
import java.util.StringJoiner

data class VersionsInfo(
    val versions: Map<String, Map<DebianVersion, String>>
)

enum class DebianVersion(val numVer: Int) {
    bookworm(12),
    bullseye(11),
    buster(10),
    ;
}

fun Project.PythonVersionsInfo(): VersionsInfo {
    return try {
        File(project.rootDir, "versions/python.json").reader().use {
            Gson.fromJson(it, VersionsInfo::class.java)
        }
    } catch (e: Exception) {
        return VersionsInfo(emptyMap())
    }
}

fun Project.PythonVersionsInfo(versions: VersionsInfo) {
    File(project.rootDir, "versions/python.json")
            .also {
                if (!it.exists()) {
                    it.createNewFile()
                }
            }
            .writeText(Gson.toJson(versions))
    val workflow = File(project.rootDir, ".github/workflows/build-image.yaml")
    val currentWorkflow = workflow.readLines()
    workflow.delete()
    workflow.createNewFile()
    workflow.writer().use { writer ->
        for (line in currentWorkflow) {
            // 修改 Python 版本
            if (line.contains("# python-versions")) {
                val curLine = StringJoiner(
                        ", ", "        version: [", "] # python-versions"
                ).also { joiner ->
                    for (ver in versions.versions.keys.map { it.replace(".", "") }) {
                        joiner.add(ver)
                    }
                }.toString()
                writer.appendLine(curLine)
                continue
            }

            // 修改 Debian 版本
            if (line.contains("# debian-versions")) {
                val curLine = StringJoiner(
                        ", ", "        platform: [", "] # debian-versions"
                ).also { joiner ->
                    for (ver in DebianVersion.values().map { it.toString().capitalized() }) {
                        joiner.add(ver)
                    }
                }.toString()
                writer.appendLine(curLine)
                continue
            }

            writer.appendLine(line)
        }
    }
}

fun Project.CudaVersionsInfo(): VersionsInfo {
    return try {
        File(project.rootDir, "versions/cuda.json").reader().use {
            Gson.fromJson(it, VersionsInfo::class.java)
        }
    } catch (e: Exception) {
        return VersionsInfo(emptyMap())
    }
}

fun Project.CudaVersionsInfo(versions: VersionsInfo) {
    File(project.rootDir, "versions/cuda.json")
            .also {
                if (!it.exists()) {
                    it.createNewFile()
                }
            }
            .writeText(Gson.toJson(versions))
    val workflow = File(project.rootDir, ".github/workflows/build-image.yaml")
    val currentWorkflow = workflow.readLines()
    workflow.delete()
    workflow.createNewFile()
    workflow.writer().use { writer ->
        for (line in currentWorkflow) {
            // 修改 Python 版本
            if (line.contains("# cuda-versions")) {
                val curLine = StringJoiner(
                        ", ", "        cuda: [", "] # cuda-versions"
                ).also { joiner ->
                    for (ver in versions.versions.keys.map { it.replace(".", "") }) {
                        joiner.add(ver)
                    }
                }.toString()
                writer.appendLine(curLine)
                continue
            }

            writer.appendLine(line)
        }
    }
}