plugins {
    id("module-java")
    id("packaging-publication")
}

description = "Chassis on Velocity: the proxy's version, and its loaded plugins for descriptors' plugins"

dependencies {
    api(projects.chassisApi)

    // Built against Velocity 3's API, whose methods used here Velocity 4 keeps unchanged, so it runs on both.
    compileOnly(libs.velocity)
    testImplementation(libs.velocity)
}
