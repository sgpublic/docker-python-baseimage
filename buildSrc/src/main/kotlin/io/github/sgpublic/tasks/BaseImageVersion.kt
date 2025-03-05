package io.github.sgpublic.tasks

import io.github.sgpublic.utils.Gson
import io.github.sgpublic.utils.NetJsonObject
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.submodule.SubmoduleWalk
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

data class BaseImageVersionInfo(
    val baseImage: String,
    val baseImageGui: String,
)

abstract class BaseImageVersion: DefaultTask() {
    private val LatestBaseImage: String get() =
        NetJsonObject("https://api.github.com/repos/jlesage/docker-baseimage/releases/latest")
            .get("tag_name").asString
    private val LatestBaseImageGui: String get() =
        NetJsonObject("https://api.github.com/repos/jlesage/docker-baseimage-gui/releases/latest")
                .get("tag_name").asString

    init {
        outputs.upToDateWhen {
            try {
                val baseImageInfo = File(project.rootDir, "versions/baseimage.json").reader().use {
                    Gson.fromJson(it, BaseImageVersionInfo::class.java)
                }
                baseImageInfo.baseImage == LatestBaseImage && baseImageInfo.baseImageGui == LatestBaseImageGui
            } catch (e: Exception) {
                false
            }
        }
    }

    @TaskAction
    fun action() {
        // baseimage
        val latestBaseImage = LatestBaseImage

        Git.wrap(SubmoduleWalk.getSubmoduleRepository(
                project.rootDir, "./src/main/base/baseimage"
        )).use { git ->
            git.fetch().call()
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(latestBaseImage).call()
        }
        // baseimage-gui
        val latestBaseImageGui = LatestBaseImageGui
        Git.wrap(SubmoduleWalk.getSubmoduleRepository(
                project.rootDir, "./src/main/base/baseimage-gui"
        )).use { git ->
            git.fetch().call()
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(latestBaseImageGui).call()
        }

        File(project.rootDir, "versions/baseimage.json").writer().use {
            it.write(Gson.toJson(BaseImageVersionInfo(
                    baseImage = latestBaseImage,
                    baseImageGui = latestBaseImageGui,
            )))
        }
    }

    override fun getGroup(): String {
        return "versions"
    }
}