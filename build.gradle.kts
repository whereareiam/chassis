import me.whereareiam.toolkit.versioning.extension.ToolkitVersioningExtension

plugins {
    alias(libs.plugins.toolkit.versioning)
}

allprojects {
    version = rootProject.extensions
        .getByType(ToolkitVersioningExtension::class.java)
        .resolvedVersion()
        .get()
}
