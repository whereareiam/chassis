plugins {
    id("module-java")
    id("packaging-publication")
}

description = "Loads the libraries Chassis extensions declare with Attache into their own class loaders"

dependencies {
    api(projects.chassisApi)

    compileOnly(libs.attache.standalone)

    testImplementation(projects.chassisCommon)
    testImplementation(projects.chassisTesting)
    testImplementation(libs.attache.standalone)
}
