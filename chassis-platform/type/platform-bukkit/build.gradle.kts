plugins {
    id("module-java")
    id("packaging-publication")
}

description = "Chassis on Bukkit, Spigot, Paper and Folia: the server's kind and game version, and its enabled plugins for descriptors' plugins"

dependencies {
    api(projects.chassisApi)

    compileOnly(libs.spigot)
    testImplementation(libs.spigot)
}
