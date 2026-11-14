import java.time.Duration

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.gradleup.shadow")
}

interface Injected {
    @get:Inject val archives: ArchiveOperations
}
val archives = objects.newInstance<Injected>().archives

val tla = configurations.create("tla")

// Libraries copied into the release jar under our own package.
val shade = configurations.create("shade")
configurations.implementation { extendsFrom(shade) }

repositories {
    maven("https://maven.notenoughupdates.org/releases/") {
        name = "NotEnoughUpdates"
        content { includeGroup("org.notenoughupdates.moulconfig") }
    }
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
    // Only debug code reads private Minecraft fields. The release build gets by without.
    accessWidenerPath = file("src/debug/resources/dsmod_debug.accesswidener")
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

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    shade("org.notenoughupdates.moulconfig:modern-${property("minecraft_version")}:${property("moulconfig_version")}")

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

tasks.named<JavaExec>("runClient") {
    val mods = devMods.elements
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dfabric.addMods=" + mods.get().joinToString(File.pathSeparator) { it.asFile.path },
                "-Ddevauth.enabled=true",
                "-Ddevauth.account=main",
            )
        },
    )
}

// MoulConfig needs its access widener declared by the mod that carries it, so take the file from its jar.
val moulconfigAccessWidener = tasks.register<Sync>("moulconfigAccessWidener") {
    val archives = archives
    from(shade.elements.map { jars -> jars.map { archives.zipTree(it) } }) { include("moulconfig.accesswidener") }
    into(layout.buildDirectory.dir("generated/moulconfig"))
}
sourceSets.main { resources.srcDir(moulconfigAccessWidener) }

tasks.withType<ProcessResources>().configureEach {
    val version = project.version
    inputs.property("version", version)
    filesMatching("fabric.mod.json") { expand("version" to version) }
}

tasks.jar {
    archiveClassifier = "thin"
    destinationDirectory = layout.buildDirectory.dir("devlibs")
}

// Release build: build/libs/dsmod-<version>.jar
tasks.shadowJar {
    archiveClassifier = ""
    configurations = emptyList()
    relocate("io.github.notenoughupdates.moulconfig", "io.github.noxaur.dsmod.deps.moulconfig")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from("../LICENSE", "NOTICE")
    val archives = archives
    val shaded = shade.elements.map { jars -> jars.map { archives.zipTree(it) } }
    // MoulConfig's jar carries LibNinePatch's fabric.mod.json and LICENSE, which would replace ours.
    from(shaded) { exclude("fabric.mod.json", "LICENSE", "moulconfig.accesswidener") }
    from(shaded) {
        include("LICENSE")
        rename { "LICENSE-LibNinePatch" }
    }
}

// Debug build: build/libs/dsmod-<version>-debug.jar, the release jar plus the debug tools.
val debugJar = tasks.register<Jar>("debugJar") {
    archiveClassifier = "debug"
    from(debug.output)
    from("../LICENSE", "NOTICE")
    from(tasks.shadowJar) { into("META-INF/jars") }
}

tasks.assemble { dependsOn(tasks.shadowJar, debugJar) }

// `./gradlew runProdClientGameTest` runs the same game tests against the built jars, as a player would load them.
dependencies {
    productionRuntimeMods("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    productionRuntimeMods("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
}
// `-Pgametests=PerfTest,ChatTest` keeps only those tests, which is how CI stays inside its time limit.
tasks.named<ProcessResources>("processGametestResources") {
    val only = providers.gradleProperty("gametests").map { it.split(",") }.orNull
    inputs.property("gametests", only ?: emptyList<String>())
    if (only != null) {
        val manifest = destinationDir.resolve("fabric.mod.json")
        doLast {
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
    // Loom would add the unshaded jar by default, so list the mods explicitly.
    mods.setFrom(debugJar, gametestJar, configurations.productionRuntimeMods)
    jvmArgs.add("-Dfabric.client.gametest")
    // PerfTest weighs the jar a player installs.
    jvmArgs.add(tasks.shadowJar.flatMap { it.archiveFile }.map { "-Ddsmod.jar=${it.asFile.path}" })
    runDir = layout.buildDirectory.dir("run/prodClientGameTest")
    // A CI runner has no display.
    useXVFB = providers.environmentVariable("CI").isPresent
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
    // RulesTest reads the sources, so a source change must rerun it.
    inputs.dir("src")
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
        inputs.files(cfg, file("../specs/$name.tla"))
        outputs.dir(meta)
    }
    tlc.configure { dependsOn(run) }
}
tasks.check { dependsOn(tlc) }
