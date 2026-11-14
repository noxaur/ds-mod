pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version").get()
        kotlin("jvm") version providers.gradleProperty("kotlin_version").get()
        kotlin("plugin.serialization") version providers.gradleProperty("kotlin_version").get()
        id("com.gradleup.shadow") version providers.gradleProperty("shadow_version").get()
    }
}

rootProject.name = "dsmod"
