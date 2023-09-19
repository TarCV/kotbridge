import groovy.json.JsonOutput
import java.nio.file.Files
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.incremental.createDirectory
import org.jetbrains.kotlin.incremental.deleteDirectoryContents

plugins {
    id("io.koalaql.kapshot-plugin")

    kotlin("jvm")

    application
}
repositories {
    mavenCentral()
}
application {
    mainClass.set("MainKt")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.12.1")
}

afterEvaluate {
    tasks.withType(KotlinCompile::class) {
        val setName = sourceSetName.get()
        val capitalizedSetName = setName.capitalized()
        tasks.named("ktJsFragments${capitalizedSetName}PrepareProject") {
            inputs.files(objects.fileTree()
                .builtBy(this@withType.name)
                .from(
                    tasks.named(this@withType.name).map {
                        it.outputs
                            .files
                            .single { it.canonicalPath.endsWith("/kotbridge/$setName/kjs") }
                    }
                )
            )
        }
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
                        kotlin("multiplatform") version "1.8.21" apply false
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


tasks.withType(Test::class) {
    useJUnitPlatform()
    include("**Tests.*")
}
