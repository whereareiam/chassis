plugins {
    id("module-java")
    id("packaging-publication")
}

description = "Makes Chassis extensions through Guice: an injector of their own with the services the host exports to them"

dependencies {
    api(projects.chassisApi)

    compileOnly(libs.guice)

    testImplementation(projects.chassisCommon)
    testImplementation(projects.chassisTesting)
    testImplementation(libs.guice)
}
