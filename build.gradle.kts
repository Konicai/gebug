import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.github.johnrengelman.shadow")
    id("io.papermc.paperweight.userdev") version "1.5.11"
    id("idea")
}

group = "me.konicai"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    compileOnly("org.projectlombok:lombok:1.18.30")

    compileOnly("org.geysermc.geyser:core:2.2.0-SNAPSHOT") {
        exclude("io.netty")
    }

    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")

    implementation("cloud.commandframework:cloud-paper:1.8.4")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    withType<ShadowJar> {
        isEnableRelocation = true
        relocationPrefix = "me.konicai.gebug.shadow"

        archiveVersion.set("")
        archiveClassifier.set("")
    }
    assemble {
        dependsOn(reobfJar)
    }
    named("build") {
        dependsOn(named("shadowJar"))
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
