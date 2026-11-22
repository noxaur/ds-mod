import java.time.Duration

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("dev.detekt:detekt-api:${property("detekt_version")}")
    testImplementation("dev.detekt:detekt-core:${property("detekt_version")}")
    testImplementation("dev.detekt:detekt-rules:${property("detekt_version")}")
    testImplementation("dev.detekt:detekt-test:${property("detekt_version")}")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
    timeout = Duration.ofSeconds(30)
    dependsOn(":lintClasspath")
    val config = rootProject.file("detekt.yml")
    val classpath = rootProject.layout.buildDirectory.file("lint/classpath").get().asFile
    inputs.file(config)
    outputs.upToDateWhen { false }
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Ddsmod.detekt=${config.path}", "-Ddsmod.classpath=${classpath.path}") })
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = false
    }
}
