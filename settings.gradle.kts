
rootProject.name = "gebug"

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    plugins {
        id("com.github.johnrengelman.shadow") version "8.1.1" // shadowing dependencies
    }
}
