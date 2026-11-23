import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("dev.detekt")
    id("net.ltgt.errorprone")
}

val tla = configurations.create("tla")

repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") {
        name = "DevAuth"
        content { includeGroup("me.djtheredstoner") }
    }
    // @keep tla2tools is published only as a GitHub release asset.
    exclusiveContent {
        forRepository {
            ivy("https://github.com/tlaplus/tlaplus/releases/download/") {
                patternLayout { artifact("v[revision]/[artifact].[ext]") }
                metadataSources { artifact() }
            }
        }
        filter { includeGroup("org.lamport") }
    }
}

val devMods = configurations.create("devMods") { isTransitive = false }

val debug = sourceSets.create("debug") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
sourceSets.test {
    compileClasspath += debug.output
    runtimeClasspath += debug.output
}

loom {
    accessWidenerPath = file("src/main/resources/dsmod.accesswidener")
    mods {
        register("dsmod") { sourceSet(sourceSets.main.get()) }
        register("dsmod_debug") { sourceSet(debug) }
    }
    runs.named("client") { setSource(debug) }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "dsmod_gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}
sourceSets.named("gametest") {
    compileClasspath += debug.output
    runtimeClasspath += debug.output
}
kotlin.target.compilations.named("gametest") { associateWith(kotlin.target.compilations.getByName("main")) }
tasks.named<ProcessResources>("processGametestResources") {
    from("src/test/resources") { include("profiles/**") }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    compileOnly("maven.modrinth:modmenu:${property("modmenu_version")}") { isTransitive = false }

    devMods("maven.modrinth:sodium:${property("sodium_version")}")
    devMods("maven.modrinth:immediatelyfast:${property("immediatelyfast_version")}")
    devMods("maven.modrinth:modmenu:${property("modmenu_version")}")
    devMods("maven.modrinth:placeholder-api:${property("placeholder_api_version")}")
    devMods("maven.modrinth:spark:${property("spark_version")}")
    devMods("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.tngtech.archunit:archunit:${property("archunit_version")}")

    tla("org.lamport:tla2tools:${property("tla_version")}@jar")
    errorprone("com.google.errorprone:error_prone_core:${property("errorprone_version")}")
}

kotlin {
    jvmToolchain(25)
}

val seedRun = tasks.register("seedRun") {
    val defaults = file("run-defaults")
    val run = file("run")
    // @keep The client keeps a server's pack at downloads/<the id the server sends>/<sha1>, and verifies the hash, so a stale copy is replaced.
    val cache = gradle.gradleUserHomeDir.resolve("caches/dsmod/dragsim.zip")
    val pack = run.resolve("downloads/c2222eea-454f-35cd-b85c-93c1c5a59e17")
    doNotTrackState("Copies into the game folder, which the game writes to")
    doLast {
        defaults.walkTopDown().filter { it.isFile }.forEach { source ->
            val target = run.resolve(source.relativeTo(defaults))
            if (!target.exists()) source.copyTo(target)
        }
        if (pack.exists()) return@doLast
        val fetched = runCatching {
            val request = HttpRequest.newBuilder(URI("https://dragsim.co/textures/dragsim.zip"))
            if (cache.exists()) {
                request.header("If-Modified-Since", RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(cache.lastModified()).atZone(ZoneOffset.UTC)))
            }
            val partial = cache.resolveSibling("dragsim.zip.part").apply { parentFile.mkdirs() }
            val transfer = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
                .sendAsync(request.build(), HttpResponse.BodyHandlers.ofFile(partial.toPath(), CREATE, WRITE, TRUNCATE_EXISTING))
            // @keep A request's own timeout ends at the headers, so a server that stalls in the body would hold the game back.
            val response = runCatching { transfer.get(2, TimeUnit.MINUTES) }.onFailure { transfer.cancel(true) }.getOrThrow()
            if (response.statusCode() == 200) {
                partial.renameTo(cache.apply { delete() })
                response.headers().firstValue("Last-Modified").ifPresent {
                    cache.setLastModified(ZonedDateTime.parse(it, RFC_1123_DATE_TIME).toInstant().toEpochMilli())
                }
            } else {
                partial.delete()
            }
        }
        fetched.exceptionOrNull()?.let { logger.warn("Could not fetch DragSim's resource pack, so the game will: $it") }
        if (!cache.exists()) return@doLast
        val sha1 = MessageDigest.getInstance("SHA-1").digest(cache.readBytes()).joinToString("") { "%02x".format(it) }
        cache.copyTo(pack.resolve(sha1))
    }
}

tasks.named<JavaExec>("runClient") {
    dependsOn(seedRun)
    val mods = devMods.elements
    val root = layout.projectDirectory
    val brief = providers.gradleProperty("brief").map { root.file(it).asFile.absolutePath }
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dfabric.addMods=" + mods.get().joinToString(File.pathSeparator) { it.asFile.path },
                "-Ddevauth.enabled=true",
                "-Ddevauth.account=main",
            ) + listOfNotNull(brief.orNull?.let { "-Ddsmod.brief=$it" })
        },
    )
}

tasks.withType<ProcessResources>().configureEach {
    val version = project.version
    val backend = providers.gradleProperty("backend_url").get()
    val website = providers.gradleProperty("website_url").get()
    inputs.property("version", version)
    inputs.property("backend", backend)
    inputs.property("website", website)
    filesMatching("fabric.mod.json") { expand("version" to version, "backend_url" to backend, "website_url" to website) }
}

tasks.jar {
    from("../LICENSE", "NOTICE")
}

val debugJar = tasks.register<Jar>("debugJar") {
    archiveClassifier = "debug"
    from(debug.output)
    from("../LICENSE", "NOTICE")
    from(tasks.jar) { into("META-INF/jars") }
}

tasks.assemble { dependsOn(debugJar) }

tasks.register("installMod") {
    group = "distribution"
    description = "Puts the release jar, or the debug jar with -Pjar=debug, in the game folder at -Pgame=, in place of any ds-mod jar there. `mise run install` picks the jar and the backend."
    val game = providers.gradleProperty("game").map { File(it).absoluteFile.normalize() }
    val jar = (if (providers.gradleProperty("jar").orNull == "debug") debugJar else tasks.jar).flatMap { it.archiveFile }
    inputs.file(jar)
    outputs.upToDateWhen { false }
    doLast {
        val mods = game.get().let { if (it.name == "mods") it else it.resolve("mods") }
        check(mods.isDirectory) { "$mods is not a mods folder. Pass the game folder that holds mods, such as a Prism instance's minecraft folder, or the mods folder." }
        val built = jar.get().asFile
        built.copyTo(mods.resolve(built.name), overwrite = true)
        val old = mods.listFiles { file -> file.name.startsWith("dsmod-") && file.name.endsWith(".jar") && file.name != built.name }.orEmpty()
        for (file in old) {
            try {
                Files.delete(file.toPath())
            } catch (e: IOException) {
                throw GradleException("Installed ${built.name}, but could not remove $file, so the game would load both. Close the game and run the install again.", e)
            }
        }
        logger.lifecycle("Installed ${built.name} in $mods")
    }
}

dependencies {
    productionRuntimeMods("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    productionRuntimeMods("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    productionRuntimeMods("maven.modrinth:modmenu:${property("modmenu_version")}") { isTransitive = false }
    "gametestImplementation"("maven.modrinth:modmenu:${property("modmenu_version")}") { isTransitive = false }
}
val gametests = providers.gradleProperty("gametests")
val gameTestManifest = file("src/gametest/fabric.mod.json")
val picked = layout.buildDirectory.dir("gametests")
val pickGameTests = tasks.register<JavaExec>("pickGameTests") {
    group = "verification"
    description = "Lists the game tests in the test mod's manifest: those -Pgametests= names, all for -Pgametests=all, or else those the change since the branch left main reaches. See GameTestPick.kt."
    // @keep The compile classpath holds all the picker loads, and the runtime one adds Loom's launch libraries, which a job that only builds the jars never fetched.
    classpath = sourceSets["debug"].let { it.output + it.compileClasspath }
    mainClass = "io.github.noxaur.dsmod.debug.GameTestPickKt"
    inputs.files(sourceSets["gametest"].output.classesDirs, gameTestManifest)
    args(listOf(projectDir.path, picked.get().asFile.path) + listOfNotNull(gametests.orNull))
    outputs.dir(picked)
    outputs.upToDateWhen { false }
}
tasks.named<ProcessResources>("processGametestResources") {
    from(pickGameTests) { include("fabric.mod.json") }
}
val gametestJar = tasks.register<Jar>("gametestJar") {
    archiveClassifier = "gametest"
    destinationDirectory = layout.buildDirectory.dir("devlibs")
    from(sourceSets["gametest"].output)
}
val cleanProdClientGameTest = tasks.register<Delete>("cleanProdClientGameTest") {
    delete(layout.buildDirectory.dir("run/prodClientGameTest/config"))
}
tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("runProdClientGameTest") {
    dependsOn(cleanProdClientGameTest)
    group = "verification"
    description = "Runs the client game tests against the release and debug jars."
    // @keep Loom would add the release jar beside the debug jar that already holds it, so the mods are listed explicitly.
    mods.setFrom(debugJar, gametestJar, configurations.productionRuntimeMods)
    jvmArgs.add("-Dfabric.client.gametest")
    jvmArgs.add(tasks.jar.flatMap { it.archiveFile }.map { "-Ddsmod.jar=${it.asFile.path}" })
    runDir = layout.buildDirectory.dir("run/prodClientGameTest")
    useXVFB = providers.environmentVariable("CI").isPresent
}
tasks.matching { it.name == "runClientGameTest" || it.name == "runProdClientGameTest" }.configureEach {
    val list = picked.map { it.file("tests").asFile }
    onlyIf("the change reaches a game test") { list.get().readLines().any(String::isNotBlank) }
}

tasks.register<JavaExec>("cutFixture") {
    group = "development"
    description = "Cuts a redacted fixture out of a capture. See CutFixture.kt."
    classpath = sourceSets["debug"].runtimeClasspath
    mainClass = "io.github.noxaur.dsmod.debug.capture.CutFixtureKt"
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 2
    timeout = Duration.ofSeconds(30)
    inputs.dir("src")
    inputs.files(sourceSets["gametest"].output.classesDirs)
    inputs.files("../README.md", "../docs/user/features.md")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = false
    }
}

val specs = fileTree("../specs") { include("*.cfg") }.files.sortedBy { it.name }
val tlc = tasks.register("tlc") {
    group = "verification"
    description = "Model-checks every TLA+ spec in specs/."
}
specs.forEach { cfg ->
    val name = cfg.nameWithoutExtension
    val run = tasks.register<JavaExec>("tlc$name") {
        group = "verification"
        description = "Model-checks specs/$name.tla."
        classpath = tla
        mainClass = "tlc2.TLC"
        workingDir = file("../specs")
        val meta = layout.buildDirectory.dir("tlc/$name")
        args("-config", cfg.name, "-metadir", meta.get().asFile.path, "-deadlock", "$name.tla")
        // @keep TLC unpacks Naturals.tla into java.io.tmpdir, and in one shared /tmp one parallel run can parse another's half-written module.
        val tmp = meta.map { it.dir("tmp") }
        jvmArgs(tmp.map { "-Djava.io.tmpdir=${it.asFile.path}" }.get())
        doFirst { tmp.get().asFile.mkdirs() }
        inputs.files(cfg, file("../specs/$name.tla"))
        outputs.dir(meta)
    }
    tlc.configure { dependsOn(run) }
}
tasks.check { dependsOn(tlc) }

dependencies {
    detektPlugins(project(":lint"))
}

detekt {
    config.setFrom("detekt.yml")
    buildUponDefaultConfig = false
}
val mixinHandlers = listOf(
    "org.spongepowered.asm.mixin.Overwrite",
    "org.spongepowered.asm.mixin.injection.Inject",
    "org.spongepowered.asm.mixin.injection.ModifyArg",
    "org.spongepowered.asm.mixin.injection.ModifyArgs",
    "org.spongepowered.asm.mixin.injection.ModifyConstant",
    "org.spongepowered.asm.mixin.injection.ModifyVariable",
    "org.spongepowered.asm.mixin.injection.Redirect",
    "com.llamalad7.mixinextras.injector.ModifyExpressionValue",
    "com.llamalad7.mixinextras.injector.ModifyReceiver",
    "com.llamalad7.mixinextras.injector.ModifyReturnValue",
    "com.llamalad7.mixinextras.injector.v2.WrapWithCondition",
    "com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod",
    "com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation",
).joinToString(",")
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
    options.errorprone {
        enabled = providers.gradleProperty("errorprone").map { it != "false" }.orElse(true)
        disableWarningsInGeneratedCode = true
        disable("InvalidBlockTag")
        option("UnusedMethod:ExemptingMethodAnnotations", mixinHandlers)
        option("Unused:methodAnnotationsExemptingParameters", mixinHandlers)
    }
}
tasks.register("lintClasspath") {
    description = "Writes the classpath that DetektConfigTest in lint/ compiles each rule's examples against."
    val classpath = sourceSets.test.get().runtimeClasspath
    val out = layout.buildDirectory.file("lint/classpath")
    inputs.files(classpath)
    outputs.file(out)
    doLast { out.get().asFile.writeText(classpath.asPath) }
}
tasks.named("detekt") { onlyIf("it resolves no types, so the lint rules run in detektMain, detektDebug, detektTest and detektGametest") { false } }
val lintCheck = tasks.register("lintCheck") {
    group = "verification"
    description = "Runs the lint rules in detekt.yml on every source set, and checks that each rule flags its examples."
    dependsOn("detektMain", "detektDebug", "detektTest", "detektGametest", ":lint:test")
}
tasks.check { dependsOn(lintCheck) }
tasks.matching { it.name.startsWith("detekt") }.configureEach { mustRunAfter(tasks.test) }
