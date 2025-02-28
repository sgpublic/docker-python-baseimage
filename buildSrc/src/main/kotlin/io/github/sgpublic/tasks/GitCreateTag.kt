package io.github.sgpublic.tasks

import org.eclipse.jgit.api.Git
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class GitCreateTag: DefaultTask() {
    override fun getGroup(): String {
        return "publishing"
    }

    @get:Input
    abstract val tagName: Property<String>

    @TaskAction
    fun execute() {
        Git.open(project.rootDir).use { git ->
            if (git.status().call().hasUncommittedChanges()) {
                throw IllegalStateException("Please commit all the change before getting linuxqq version info!")
            }
            if (!tagName.isPresent) {
                throw IllegalStateException("Please pass a tag name for adding.")
            }
            git.tag()
                .setName(tagName.get())
                .call()
        }
    }
}