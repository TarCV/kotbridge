plugins {
    id("conventions")

    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.1.0"

    id("com.github.gmazzo.buildconfig") version "3.1.0"
}

buildConfig {
    buildConfigField("String", "VERSION", "\"${project.version}\"")
}

dependencies {
    implementation(kotlin("gradle-plugin"))
}

gradlePlugin {
    website.set("https://github.com/TarCV/kotbridge")
    vcsUrl.set("https://github.com/TarCV/kotbridge.git")

    plugins {
        create("kotbridgePlugin") {
            id = "com.github.tarcv.kotbridge-plugin"
            displayName = "Kotbridge Plugin"
            description = "Kotlin Compiler Plugin for compiling source fragments as Kotlin/JS"
            implementationClass = "com.github.tarcv.kotbridge.GradlePlugin"

            tags.set(listOf("kotlin", "kotbridge", "jvm"))
        }
    }
}
