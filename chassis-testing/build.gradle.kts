plugins {
    id("module-java")
    id("packaging-publication")
}

description = "Test helpers for Chassis hosts: extension jars written on the fly, and the check that a stopped extension's class loader is collected"

dependencies {
    api(projects.chassisApi)
}
