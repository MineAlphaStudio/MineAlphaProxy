plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

group = "com.minealphaproxy"
version = "1.0.2-ALPHA"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
    implementation("io.netty:netty-all:4.1.115.Final")
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("com.google.inject:guice:7.0.0")

    implementation("net.kyori:adventure-api:4.24.0")
    implementation("net.kyori:adventure-text-serializer-gson:4.24.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.24.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.24.0")
    implementation("net.kyori:adventure-nbt:4.24.0")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    withType<ProcessResources> { filteringCharset = "UTF-8" }

    shadowJar {
        archiveBaseName.set("MineAlphaProxy")
        archiveClassifier.set("")
        manifest {
            attributes["Main-Class"] = "com.minealphaproxy.MineAlphaProxy"
            attributes["Implementation-Title"] = "MineAlphaProxy"
            attributes["Implementation-Version"] = project.version
        }
        relocate("com.fasterxml.jackson", "com.minealphaproxy.libs.jackson")
        relocate("org.tomlj", "com.minealphaproxy.libs.tomlj")
        relocate("io.netty", "com.minealphaproxy.libs.netty")
        relocate("org.yaml.snakeyaml", "com.minealphaproxy.libs.snakeyaml")
        relocate("com.sun.jna", "com.minealphaproxy.libs.jna")
    }

    build { dependsOn(shadowJar) }
}