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
        id("dev.detekt") version providers.gradleProperty("detekt_version").get()
        id("net.ltgt.errorprone") version providers.gradleProperty("errorprone_plugin_version").get()
    }
}

rootProject.name = "dragen"

include("lint")
