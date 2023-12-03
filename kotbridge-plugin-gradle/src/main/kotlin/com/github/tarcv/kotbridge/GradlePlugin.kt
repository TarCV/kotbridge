package com.github.tarcv.kotbridge

import com.github.tarcv.kotbridge_plugin_gradle.BuildConfig
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import groovy.json.JsonOutput
import org.gradle.api.model.ObjectFactory
import java.nio.file.Files
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import javax.inject.Inject

class GradlePlugin @Inject constructor(private val objects: ObjectFactory) : KotlinCompilerPluginSupportPlugin {
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

            val compileTaskClass: Class<out BaseKotlinCompile> = run {
                val clazz = try {
                    Class.forName("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
                } catch (e: ClassNotFoundException) {
                    error("Kotlin plugin is required but it is not loaded: $e")
                }
                clazz.asSubclass(BaseKotlinCompile::class.java)
            }
            target.tasks.withType(compileTaskClass) { compileTask ->
                val setName = compileTask.sourceSetName.get()
                val capitalizedSetName = setName.capitalized()
                target.tasks.register("ktJsFragments${capitalizedSetName}PrepareProject", PrepareBuildJsProjectTask::class.java) {
                        prepareProject ->
                }
                target.tasks.named("ktJsFragments${capitalizedSetName}PrepareProject") { prepareProject ->
                    prepareProject.inputs.files(objects.fileTree()
                        .builtBy(compileTask.name)
                        .from(
                            target.tasks.named(compileTask.name).map {
                                it.outputs
                                    .files
                                    .single { it.canonicalPath.endsWith("/kotbridge/$setName/kjs") }
                            }
                        )
                    )
                }
            }

            sourceSets.forEach { set ->
                val setName = set.name
                val capitalizedSetName = setName.capitalized()
                val baseSetOutputDir = buildDir
                    .resolve("generated")
                    .resolve("kotbridge")
                    .resolve(setName)

                tasks.register("ktJsFragments${capitalizedSetName}PrepareProject") {
                    val outputDir = baseSetOutputDir.resolve("project")

                    outputs.dir(outputDir)

                    doLast {
                        if (outputDir.isDirectory) {
                            outputDir.deleteDirectoryContents()
                        } else if (outputDir.isFile) {
                            Files.delete(outputDir.toPath())
                        }

                        val fragmentFiles = inputs.files.let {
                            if (it.isEmpty) {
                                return@doLast
                            }
                            it.files
                        }

                        // This generated project discovers incoming kt/js fragments by itself,
                        // so that calling `gradlew :allJsBrowserProductionWebpack` manually would generate expected files

                        outputDir
                            .createDirectory()

                        outputDir
                            .resolve("build.gradle.kts") // TODO: Use kotlin version from 'testing' module
                            .writeText(/* language=gradle.kts */"""
                    import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
                    
                    plugins {
                        kotlin("multiplatform") version "1.9.20" apply false
                    }
                    repositories {
                        mavenCentral()
                    }
                    
                    buildDir = file("${'$'}projectDir/../projectBuild/rootBuild")
                    
                    val allJsBrowserProductionWebpack by tasks.creating(Copy::class) {
                        into("${'$'}buildDir/allJs")
                        duplicatesStrategy = DuplicatesStrategy.FAIL
                    }
                    
                    subprojects {
                        apply(plugin = "org.jetbrains.kotlin.multiplatform")
                        repositories {
                            mavenCentral()
                        }
                    
                        extensions.configure(KotlinMultiplatformExtension::class) {
                            js(IR) {
                                sourceSets.named("jsMain") {
                                    kotlin.srcDir("${'$'}projectDir/../../../../../../../src/test/ktJs")
                                    kotlin.srcDir("src")
                                }
                                browser {
                                    webpackTask {
                                        allJsBrowserProductionWebpack.exclude(configFile.map { "**/${'$'}{it.name}" }.get())
                                    }
                                    commonWebpackConfig {
                                        outputFileName = "${'$'}{this@subprojects.name}.js"
                                        sourceMaps = false
                                        cssSupport {
                                            enabled.set(false)
                                        }
                                    }
                                }
                                useCommonJs()
                                binaries.executable() // webpack is currently enabled only for executables
                            }
                        }
                    }
                    afterEvaluate {
                        subprojects {
                            val task: Task = this@subprojects.tasks.getByName("jsBrowserProductionWebpack")
                            allJsBrowserProductionWebpack.apply {
                                from(task.outputs)
                            }
                        }
                    }
                    """.trimIndent()
                            )
                        outputDir
                            .resolve("gradle.properties")
                            .writeText(/* language=properties */"""
                    kotlin.js.ir.output.granularity=whole-program
                """.trimIndent()
                            )
                        outputDir
                            .resolve("inputs.json")
                            .writeText(
                                fragmentFiles
                                    .map { it.toRelativeString(outputDir) }
                                    .let { JsonOutput.toJson(it) }
                            )
                        outputDir // TODO: fix caching/incremental build
                            .resolve("settings.gradle.kts")
                            .writeText(/* language=gradle.kts */"""
                    import groovy.json.JsonSlurper
                    import java.nio.file.Files

                    val subprojectBaseDir = file("${'$'}rootDir/../projectBuild/subprojects")
                    (JsonSlurper().parse(file("inputs.json")) as List<String>)
                        .forEach { 
                            val inputFile = file(it)
                            var uniqueName = inputFile.nameWithoutExtension
        
                            include(uniqueName)
                            project(":${'$'}{uniqueName}").apply {
                                projectDir = file("${'$'}subprojectBaseDir/${'$'}{uniqueName}").apply {
                                    java.nio.file.Files.createDirectories(toPath())
                                    resolve("build.gradle.kts").writeText("")
        
                                    inputFile.copyTo(
                                        resolve("src").resolve("index.kt"),
                                        overwrite = true
                                    )
                                }
                            }
                        }
                    """.trimIndent()
                            )
                    }
                }

                tasks.register("ktJsFragments${capitalizedSetName}Build", Exec::class) {
                    val upstreamTask = tasks.named("ktJsFragments${capitalizedSetName}PrepareProject")
                    val compiledFiles = upstreamTask.map { task -> task.inputs.files }
                    val compilingProjectDir = upstreamTask.map { task -> task.outputs.files.singleFile }
                    val compilingBuildDir = baseSetOutputDir.resolve("projectBuild")

                    inputs.files(compiledFiles)
                    inputs.files(compilingProjectDir)
                    outputs.dir(compilingBuildDir.toString())
                    onlyIf {
                        compilingProjectDir
                            .map {
                                it.walk().maxDepth(1).drop(1).any()
                            }
                            .get()
                    }

                    commandLine("${rootDir}/gradlew", ":allJsBrowserProductionWebpack")
                    workingDir(compilingProjectDir)
                }
                set.resources.srcDir(
                    tasks.named("ktJsFragments${capitalizedSetName}Build")
                        .map { task ->
                            task.outputs
                                .files
                                .singleFile
                                .resolve("rootBuild")
                                .resolve("allJs")
                        }
                )
            }
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
