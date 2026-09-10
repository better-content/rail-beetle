plugins {
    idea
    `java-library`
    id("net.minecraftforge.gradle") version "[6.0.24,6.2)"
}

val minecraftVersion = property("minecraft_version") as String
val forgeVersion = property("forge_version") as String
val createVersion = property("create_maven_version") as String
val ponderVersion = property("ponder_version") as String
val flywheelVersion = property("flywheel_version") as String
val registrateVersion = property("registrate_version") as String
val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val unitTestRuntime by configurations.creating

group = property("mod_group") as String
version = modVersion
base { archivesName.set(property("artifact_name") as String) }

fun deobf(notation: String): Any =
    requireNotNull(extensions.getByName("fg").withGroovyBuilder { "deobf"(notation) })

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

minecraft {
    mappings("official", minecraftVersion)
    copyIdeResources = true
    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "info")
            mods { create(modId) { source(sourceSets.main.get()) } }
        }
        create("client")
        create("server") { arg("--nogui") }
        create("gameTestServer") {
            workingDirectory(project.file("run-gametest"))
            property("forge.enableGameTest", "true")
            property("forge.gameTestServer", "true")
            property("forge.enabledGameTestNamespaces", modId)
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)
            arg("--nogui")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/mirror")
    maven("https://www.cursemaven.com") { content { includeGroup("curse.maven") } }
}

dependencies {
    minecraft("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")
    implementation(deobf("com.simibubi.create:create-$minecraftVersion:$createVersion:slim"))
    implementation(deobf("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion"))
    compileOnly(deobf("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersion:$flywheelVersion"))
    runtimeOnly(deobf("dev.engine-room.flywheel:flywheel-forge-$minecraftVersion:$flywheelVersion"))
    implementation(deobf("com.tterrag.registrate:Registrate:$registrateVersion"))
    implementation("io.github.llamalad7:mixinextras-forge:0.5.4")
    compileOnly(deobf("curse.maven:sodiumdynamiclights-551736:6044481"))
    compileOnly(deobf("curse.maven:goety-586095:8087429"))

    runtimeOnly(deobf("curse.maven:citadel-331936:7476570"))
    runtimeOnly(deobf("curse.maven:alexs-caves-924854:5848216"))
    runtimeOnly(deobf("curse.maven:yungs-api-421850:5769971"))
    runtimeOnly(deobf("curse.maven:yungs-better-caves-340583:8686226"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    unitTestRuntime("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.processResources {
    val props = mapOf(
        "minecraftVersion" to minecraftVersion,
        "forgeVersion" to forgeVersion,
        "modId" to modId,
        "modName" to project.property("mod_name"),
        "modVersion" to modVersion,
        "modAuthors" to project.property("mod_authors"),
        "modDescription" to project.property("mod_description"),
        "modLicense" to project.property("mod_license")
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
}

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Deterministic unit tests do not need the heavyweight cave-integration runtime.
    classpath = sourceSets["test"].output + sourceSets["main"].output +
            configurations.testCompileClasspath.get() + unitTestRuntime
}
tasks.named<Jar>("jar") { finalizedBy("reobfJar") }

val stageRuntimeJar by tasks.registering(Copy::class) {
    dependsOn(tasks.named("reobfJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$modVersion.jar" }
}

val cleanGameTestWorld by tasks.registering(Delete::class) {
    delete(layout.projectDirectory.dir("run-gametest/world"))
    delete(layout.projectDirectory.dir("run-gametest/logs"))
}
val syncGameTestStructures by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("src/main/resources/gameteststructures"))
    into(layout.projectDirectory.dir("run-gametest/gameteststructures"))
}
tasks.matching { it.name.startsWith("prepareRunGameTestServer") }.configureEach {
    dependsOn(cleanGameTestWorld, syncGameTestStructures)
}

tasks.named("assemble") { dependsOn(stageRuntimeJar) }
tasks.register("verifyFast") { dependsOn(tasks.named("test"), tasks.named("assemble")) }
tasks.register("verifyFull") { dependsOn(tasks.named("verifyFast"), tasks.named("runGameTestServer")) }
