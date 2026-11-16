import java.net.URI
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

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    kotlin("plugin.serialization")
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
    // tla2tools is published only as a GitHub release asset.
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

// Third-party mods that `runClient` loads beside ds-mod, as a player's instance would have them.
val devMods = configurations.create("devMods") { isTransitive = false }

// `main` is the release build. `debug` holds developer tools and ships only in the debug jar.
val debug = sourceSets.create("debug") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
sourceSets.test {
    compileClasspath += debug.output
    runtimeClasspath += debug.output
}

loom {
    // The recorder reads private Minecraft fields.
    accessWidenerPath = file("src/main/resources/dsmod.accesswidener")
    mods {
        register("dsmod") { sourceSet(sourceSets.main.get()) }
        register("dsmod_debug") { sourceSet(debug) }
    }
    runs.named("client") { setSource(debug) }
}

// `./gradlew runClientGameTest` boots the real client and runs the tests in src/gametest.
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
// The backend's answers that the unit tests read, so the game test draws the same profile.
tasks.named<ProcessResources>("processGametestResources") {
    from("src/test/resources") { include("profiles/**") }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    // Mod Menu's config button opens the settings. Only its API is compiled against, and a player without it never loads that class.
    compileOnly("maven.modrinth:modmenu:${property("modmenu_version")}") { isTransitive = false }

    devMods("maven.modrinth:sodium:${property("sodium_version")}")
    devMods("maven.modrinth:immediatelyfast:${property("immediatelyfast_version")}")
    devMods("maven.modrinth:modmenu:${property("modmenu_version")}")
    devMods("maven.modrinth:placeholder-api:${property("placeholder_api_version")}")
    devMods("maven.modrinth:spark:${property("spark_version")}")
    // Logs runClient in with a real Microsoft account, so it can join online servers.
    devMods("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    tla("org.lamport:tla2tools:${property("tla_version")}@jar")
}

kotlin {
    jvmToolchain(25)
}

// A fresh `run/` starts with the user's dev settings from `run-defaults/` and DragSim's resource pack. Each file is
// copied only when `run/` lacks it, so what the user changes in game stays.
val seedRun = tasks.register("seedRun") {
    val defaults = file("run-defaults")
    val run = file("run")
    // The pack is 40 MB, so it lives in the Gradle home, shared by every checkout, and never in git. The client keeps a
    // server's pack at downloads/<the id the server sends>/<sha1>, and verifies the hash, so a stale copy is replaced.
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
            // A request's own timeout ends at the headers, so a server that stalls in the body would hold the game back.
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

// `./gradlew runClient -Pbrief=<file>` names the window after the brief and shows its steps. See Brief.kt.
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

// Release build: build/libs/dsmod-<version>.jar
tasks.jar {
    from("../LICENSE", "NOTICE")
}

// Debug build: build/libs/dsmod-<version>-debug.jar, the release jar plus the debug tools.
val debugJar = tasks.register<Jar>("debugJar") {
    archiveClassifier = "debug"
    from(debug.output)
    from("../LICENSE", "NOTICE")
    from(tasks.jar) { into("META-INF/jars") }
}

tasks.assemble { dependsOn(debugJar) }

// `./gradlew runProdClientGameTest` runs the same game tests against the built jars, as a player would load them.
dependencies {
    productionRuntimeMods("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    productionRuntimeMods("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    // SettingsScreenTest presses Mod Menu's config button, so both game test runs load Mod Menu.
    productionRuntimeMods("maven.modrinth:modmenu:${property("modmenu_version")}") { isTransitive = false }
    "gametestImplementation"("maven.modrinth:modmenu:${property("modmenu_version")}") { isTransitive = false }
}
// A run with no `-Pgametests` runs the tests that the change since its last green run reaches, as GameTestPick.kt
// picks them, and the pre-commit hook starts one. `-Pgametests=PerfTest,ChatTest` names them, and `-Pgametests=all`
// runs every one, which takes minutes, so it is for the beta pipeline.
val gametests = providers.gradleProperty("gametests")
val picked = layout.buildDirectory.file("gametests/picked")
val pickGameTests = tasks.register<JavaExec>("pickGameTests") {
    group = "verification"
    description = "Picks the game tests that the change since the last green run reaches. See GameTestPick.kt."
    classpath = sourceSets["debug"].runtimeClasspath
    mainClass = "io.github.noxaur.dsmod.debug.GameTestPickKt"
    args(projectDir.path, picked.get().asFile.path)
    outputs.file(picked)
    outputs.upToDateWhen { false }
}
tasks.named<ProcessResources>("processGametestResources") {
    val named = gametests.map { it.split(",") }.filter { it != listOf("all") }.orNull
    val pick = picked.get().asFile.takeIf { !gametests.isPresent }
    if (pick != null) {
        dependsOn(pickGameTests)
        inputs.file(picked)
    } else {
        inputs.property("gametests", named ?: emptyList<String>())
    }
    if (named != null || pick != null) {
        val manifest = destinationDir.resolve("fabric.mod.json")
        doLast {
            val only = named ?: pick!!.readLines().filter(String::isNotBlank)
            @Suppress("UNCHECKED_CAST")
            val json = groovy.json.JsonSlurper().parse(manifest) as MutableMap<String, Any>
            val entrypoints = json["entrypoints"] as MutableMap<String, List<Map<String, String>>>
            val kept = entrypoints.getValue("fabric-client-gametest").filter { it.getValue("value").substringAfterLast('.') in only }
            check(kept.size == only.size) { "-Pgametests=$only names a test that fabric.mod.json does not list" }
            entrypoints["fabric-client-gametest"] = kept
            manifest.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)))
        }
    }
}
val gametestJar = tasks.register<Jar>("gametestJar") {
    archiveClassifier = "gametest"
    destinationDirectory = layout.buildDirectory.dir("devlibs")
    from(sourceSets["gametest"].output)
}
// Loom keeps this run folder, and a config a test saved last time would change what the tests see.
val cleanProdClientGameTest = tasks.register<Delete>("cleanProdClientGameTest") {
    delete(layout.buildDirectory.dir("run/prodClientGameTest/config"))
}
tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("runProdClientGameTest") {
    dependsOn(cleanProdClientGameTest)
    group = "verification"
    description = "Runs the client game tests against the release and debug jars."
    // Loom would add the release jar beside the debug jar that already holds it, so list the mods explicitly.
    mods.setFrom(debugJar, gametestJar, configurations.productionRuntimeMods)
    jvmArgs.add("-Dfabric.client.gametest")
    // PerfTest weighs the jar a player installs.
    jvmArgs.add(tasks.jar.flatMap { it.archiveFile }.map { "-Ddsmod.jar=${it.asFile.path}" })
    runDir = layout.buildDirectory.dir("run/prodClientGameTest")
    // A CI runner has no display.
    useXVFB = providers.environmentVariable("CI").isPresent
}
// A picked run is skipped when the change reaches no test. Only a green run on the jars a player installs keeps the
// tree it ran on, so a debug run never lets a commit skip them.
if (!gametests.isPresent) {
    val pick = picked.get().asFile
    val tree = pick.resolveSibling("tree")
    val passed = pick.resolveSibling("passed")
    tasks.matching { it.name == "runClientGameTest" || it.name == "runProdClientGameTest" }.configureEach {
        dependsOn(pickGameTests)
        onlyIf("the change reaches a game test") { pick.readLines().any(String::isNotBlank) }
        if (name == "runProdClientGameTest") doLast { tree.copyTo(passed, overwrite = true) }
    }
}

// `./gradlew cutFixture --args="<capture> <feature id> <name> <from>-<to> [--types a,b] [--info Key,Key] [--system regex]"`
tasks.register<JavaExec>("cutFixture") {
    group = "development"
    description = "Cuts a redacted fixture out of a capture. See CutFixture.kt."
    classpath = sourceSets["debug"].runtimeClasspath
    mainClass = "io.github.noxaur.dsmod.debug.capture.CutFixtureKt"
}

tasks.test {
    useJUnitPlatform()
    // The unit tests are the fast loop. One that needs longer belongs in a game test.
    timeout = Duration.ofSeconds(30)
    // RulesTest reads the sources and the docs that list features, so a change to either must rerun it.
    inputs.dir("src")
    inputs.file("input-sites.txt")
    inputs.files("../README.md", "../docs/user/features.md")
    // The user's yes to an input site, which InputSitesTest records with their GitHub login.
    systemProperty("inputSites.approve", providers.gradleProperty("approve").getOrElse(""))
    systemProperty("inputSites.approvedBy", providers.gradleProperty("approvedBy").getOrElse(""))
    // Print the whole failure, so a broken rule shows its fix in the terminal.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = false
    }
}

// One TLC run per specs/<Name>.tla that has a specs/<Name>.cfg beside it.
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
        // TLC unpacks Naturals.tla and the other standard modules into java.io.tmpdir. The specs run in
        // parallel, and in one shared /tmp one run can parse another's half-written module.
        val tmp = meta.map { it.dir("tmp") }
        jvmArgs(tmp.map { "-Djava.io.tmpdir=${it.asFile.path}" }.get())
        doFirst { tmp.get().asFile.mkdirs() }
        inputs.files(cfg, file("../specs/$name.tla"))
        outputs.dir(meta)
    }
    tlc.configure { dependsOn(run) }
}
tasks.check { dependsOn(tlc) }
