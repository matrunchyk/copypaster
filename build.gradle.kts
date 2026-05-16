plugins {
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    java
}

version = "1.1.1"
group = "com.crazyhouse.copypaster"

base {
    archivesName.set("copy_paster")
}

dependencies {
    minecraft("com.mojang:minecraft:26.1.2")
    implementation("net.fabricmc:fabric-loader:0.19.2")
    implementation("net.fabricmc.fabric-api:fabric-api:0.148.2+26.1.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}
