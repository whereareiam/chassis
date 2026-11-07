plugins {
    id("module-java")
    id("packaging-publication")
}

description = "Chassis's standard host: discovery, resolution, isolated loading, lifecycle, selection and services"

dependencies {
    api(projects.chassisApi)

    implementation(libs.configura)
}

dependencies {
    testImplementation(projects.chassisTesting)
}
