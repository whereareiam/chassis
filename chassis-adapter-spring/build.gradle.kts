plugins {
    id("module-java")
    id("packaging-publication")
}

description = "Makes Chassis extensions through Spring: a child context of the host's per extension, with what its dependencies export"

dependencies {
    api(projects.chassisApi)

    compileOnly(libs.spring.context)

    testImplementation(projects.chassisCommon)
    testImplementation(projects.chassisTesting)
    testImplementation(libs.spring.context)
}
