package com.github.tarcv.kotbridge

import com.github.tarcv.kotbridge_plugin_gradle.BuildConfig
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class GradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "com.github.tarcv.kotbridge-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.github.tarcv",
        artifactId = "kotbridge-plugin-kotlin",
        version = BuildConfig.VERSION
    )

    override fun apply(target: Project) {
        /* make sure we don't try to add dependency until it has been configured by kotlin plugin */
        target.plugins.withId("org.jetbrains.kotlin.jvm") {
            target.dependencies.add("implementation", "com.github.tarcv:kotbridge-runtime:${BuildConfig.VERSION}")
        }
    }

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extractionDir = project.buildDir
            .resolve("generated")
            .resolve("kotbridge")
            .resolve(kotlinCompilation.name)
            .resolve("kjs")
        // TODO: Remove files related to deleted fragments while not breaking incremental build

        kotlinCompilation.compileTaskProvider.configure { 
            it.outputs.dir(extractionDir)
        }
        return project.provider {
            listOf(
                SubpluginOption("projectDir", project.projectDir.path),
                SubpluginOption("extractedDir", extractionDir.path),
            )
        }
    }
}
