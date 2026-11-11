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
        // The Bukkit and Velocity platforms compile against Spigot's and Velocity's API.
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

include(":chassis-api")
project(":chassis-api").projectDir = file("chassis-api")

include(":chassis-common")
project(":chassis-common").projectDir = file("chassis-common")


include(":chassis-adapter-guice")
project(":chassis-adapter-guice").projectDir = file("chassis-adapter-guice")

include(":chassis-adapter-commandant")
project(":chassis-adapter-commandant").projectDir = file("chassis-adapter-commandant")

include(":platform-neoforge")
project(":platform-neoforge").projectDir = file("chassis-platform/type/platform-neoforge")

include(":platform-bukkit")
project(":platform-bukkit").projectDir = file("chassis-platform/type/platform-bukkit")

include(":platform-velocity")
project(":platform-velocity").projectDir = file("chassis-platform/type/platform-velocity")

include(":chassis-testing")
project(":chassis-testing").projectDir = file("chassis-testing")
