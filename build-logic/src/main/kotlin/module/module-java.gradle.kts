plugins {
    `java-library`
    id("io.freefair.lombok")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "me.whereareiam.chassis"

// Compilation and tests use Java 21 whichever Java runs Gradle.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    add("compileOnly", libs.findLibrary("annotations").get())

    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testImplementation", libs.findLibrary("annotations").get())

    add("testRuntimeOnly", libs.findLibrary("junit-platform").get())
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
