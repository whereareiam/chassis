plugins {
    id("module-java")
    id("packaging-publication")
}

description = "The commands of a host's extensions, mounted under its root command with its kind word, on Cloud and Commandant"

dependencies {
    api(projects.chassisApi)

    compileOnly(libs.cloud.core)
    compileOnly(libs.commandant.api) { isTransitive = false }
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    testImplementation(libs.cloud.core)
    testImplementation(libs.commandant.api) { isTransitive = false }
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.plain)
}
