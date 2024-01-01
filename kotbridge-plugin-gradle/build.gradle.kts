plugins {
    id("conventions")

    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "1.1.0"

    id("com.github.gmazzo.buildconfig") version "5.2.0"
}

buildConfig {
    buildConfigField("String", "VERSION", "\"${project.version}\"")
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))
}

gradlePlugin {
    website.set("https://github.com/mfwgenerics/kapshot")
    vcsUrl.set("https://github.com/mfwgenerics/kapshot.git")

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
