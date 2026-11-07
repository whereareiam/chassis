plugins {
    id("module-java")
    id("packaging-publication")
}

description = "Chassis on NeoForge: the platform's name and its loaded mods for descriptors' plugins"

dependencies {
    api(projects.chassisApi)

    compileOnly(libs.fancymodloader) { isTransitive = false }
}
