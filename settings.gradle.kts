rootProject.name = "Chassis"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
    }
}

plugins {
    // Adds the repositories from which the NeoForge platform's build resolves Minecraft and NeoForge.
    id("net.neoforged.moddev.repositories") version "2.0.148"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
    }
}

include(":chassis-api")
project(":chassis-api").projectDir = file("chassis-api")

include(":chassis-common")
project(":chassis-common").projectDir = file("chassis-common")


include(":chassis-adapter-guice")
project(":chassis-adapter-guice").projectDir = file("chassis-adapter-guice")

include(":platform-neoforge")
project(":platform-neoforge").projectDir = file("chassis-platform/type/platform-neoforge")

include(":chassis-testing")
project(":chassis-testing").projectDir = file("chassis-testing")
