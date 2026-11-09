plugins {
    id("module-java")
    id("packaging-publication")
}

description = "Loads the libraries a Chassis extension declares through Attache into the extension's own class loader"

dependencies {
    api(projects.chassisApi)

    compileOnly(libs.attache.launcher)

    testImplementation(projects.chassisCommon)
    testImplementation(projects.chassisTesting)
    testImplementation(libs.attache.launcher)
}
