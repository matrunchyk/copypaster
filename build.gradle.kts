plugins {
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    java
}

version = "3.1.0"
group = "com.crazyhouse.copypaster"

base {
    archivesName.set("copy_paster")
}

repositories {
    mavenCentral()
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:26.1.2")
    implementation("net.fabricmc:fabric-loader:0.19.2")
    implementation("net.fabricmc.fabric-api:fabric-api:0.148.2+26.1.2")
    implementation("org.yaml:snakeyaml:2.3")
    include("org.yaml:snakeyaml:2.3")

    compileOnly("maven.modrinth:cloth-config:26.1.154+fabric")
    compileOnly("maven.modrinth:modmenu:18.0.0-beta.1")
    compileOnly("me.lucko:fabric-permissions-api:0.4.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

val buildWeb = tasks.register<Exec>("buildWeb") {
    group = "build"
    description = "Build the embedded web UI (requires Node.js)"
    workingDir = file("web")
    commandLine(
        if (System.getProperty("os.name").lowercase().contains("win")) "npm.cmd" else "npm",
        "run", "build"
    )
    onlyIf {
        System.getenv("SKIP_WEB_BUILD") == null
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(buildWeb)
    inputs.dir("src/main/resources/copypaster/web")
}
